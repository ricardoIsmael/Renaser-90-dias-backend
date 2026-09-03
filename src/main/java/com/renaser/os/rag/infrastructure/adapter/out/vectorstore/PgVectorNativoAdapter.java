package com.renaser.os.rag.infrastructure.adapter.out.vectorstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.rag.application.ports.out.conocimiento.SaveChunkConocimientoPort;
import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort;
import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort.FiltroLecciones;
import com.renaser.os.rag.application.ports.out.ia.EmbeddingPort;
import com.renaser.os.rag.domain.model.conocimiento.ChunkConocimiento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * D-45: puerto propio con SQL nativo contra {@code base_conocimiento} — NO
 * {@code org.springframework.ai.vectorstore.pgvector.PgVectorStore} (columnas
 * {@code id/content/metadata/embedding} hardcodeadas en su SQL; nuestra tabla usa
 * {@code contenido}/{@code metadatos} en español y tiene {@code tipo_fuente NOT NULL},
 * que esa clase no sabría llenar — ver docs/MODULO_RAG.md D-45).
 *
 * <p>Operador {@code <=>} (distancia coseno): es el que corresponde al índice HNSW
 * {@code base_conocimiento_emb_hnsw ... vector_cosine_ops} ya existente en la tabla —
 * {@code <->} (euclídea) o {@code <#>} (producto interno) no lo usarían.
 *
 * <p>{@link VectorStorePort#buscarSimilares} recibe texto, no un vector: este adaptador
 * llama a {@link EmbeddingPort} para convertir la consulta antes de preguntarle a
 * Postgres — por eso depende de otro puerto "out" además de {@link EntityManager}.
 */
@Component
class PgVectorNativoAdapter implements VectorStorePort, SaveChunkConocimientoPort {

    /** ?1 y ?2 son el MISMO vector (SELECT y ORDER BY) — parametros separados para no depender de reuso de ordinal en Hibernate. */
    private static final String BUSCAR_SIMILARES = """
            SELECT contenido, leccion_id, embedding <=> CAST(?1 AS vector) AS distancia
            FROM renaser.base_conocimiento
            ORDER BY embedding <=> CAST(?2 AS vector)
            LIMIT ?3
            """;

    /**
     * Mismo SELECT que {@link #BUSCAR_SIMILARES}, con el WHERE del bloqueo por lección (Tarea
     * del bug de catálogo, docs/MODULO_RAG.md): un chunk pasa si NO esta ligado a ninguna
     * lección puntual ({@code leccion_id IS NULL}, material general) o si su lección esta en
     * la lista de visibles que ya resolvió {@code ConversacionRenasiaService} vía
     * {@code ConsultarLeccionesVisiblesPort}. El filtro va en el WHERE, antes del
     * {@code ORDER BY ... LIMIT} — no despues en Java — para no devolver menos de {@code topK}
     * fragmentos cuando hay candidatos bloqueados de por medio (ver javadoc de
     * {@link VectorStorePort}).
     */
    private static final String BUSCAR_SIMILARES_VISIBLES = """
            SELECT contenido, leccion_id, embedding <=> CAST(?1 AS vector) AS distancia
            FROM renaser.base_conocimiento
            WHERE leccion_id IS NULL OR leccion_id = ANY(CAST(?4 AS text[]))
            ORDER BY embedding <=> CAST(?2 AS vector)
            LIMIT ?3
            """;

    private static final String INSERTAR_CHUNK = """
            INSERT INTO renaser.base_conocimiento
                (id, tipo_fuente, clase, documento_id, leccion_id, contenido, embedding, metadatos, creado_en)
            VALUES (?1, ?2, ?3, ?4, ?5, ?6, CAST(?7 AS vector), CAST(?8 AS jsonb), ?9)
            """;

    private final EntityManager entityManager;
    private final EmbeddingPort embeddingPort;

    /**
     * {@link ObjectMapper} propio, NO inyectado (E-33, docs/BITACORA_ERRORES.md): Spring Boot
     * 4.1 autoconfigura el {@code ObjectMapper} de Jackson 3 ({@code tools.jackson.databind}),
     * no el clasico de {@code com.fasterxml} que usa esta clase — pedirlo por constructor
     * tumbaba el contexto entero con "No qualifying bean of type ObjectMapper". Serializar el
     * jsonb de {@code metadatos} es una necesidad interna y acotada; no justifica arrastrar el
     * mapper de toda la app.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    PgVectorNativoAdapter(EntityManager entityManager, EmbeddingPort embeddingPort) {
        this.entityManager = entityManager;
        this.embeddingPort = embeddingPort;
    }

    /**
     * <b>Sin {@code @Transactional} a proposito (C-1/C-4).</b> Este metodo empieza llamando a
     * {@link EmbeddingPort}, que manana es una llamada de red a un proveedor de embeddings.
     * Con la anotacion puesta, esa llamada corria dentro de la transaccion y retenia una
     * conexion de Hikari durante todo el viaje de red — exactamente el agotamiento de pool que
     * la auditoria de concurrencia del 2026-09-01 diagnostico en {@code onboarding} y corrigio
     * en {@code evidence}, y que aca nadie habia tomado.
     *
     * <p>No hace falta transaccion explicita: es un unico SELECT sin escritura, y el
     * {@code EntityManager} compartido abre y cierra el suyo para la consulta. Partirlo en dos
     * metodos y anotar el segundo no serviria — una autoinvocacion no pasa por el proxy de
     * Spring, asi que la anotacion quedaria ignorada y el problema seguiria igual pero
     * escondido.
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<FragmentoRelevante> buscarSimilares(String consulta, int topK, FiltroLecciones filtro) {
        // Fuera de cualquier transaccion: ver el javadoc de arriba.
        String vectorLiteral = aLiteralVector(embeddingPort.generar(consulta));
        Query query = switch (filtro) {
            case FiltroLecciones.SinFiltro ignorado -> entityManager.createNativeQuery(BUSCAR_SIMILARES)
                    .setParameter(1, vectorLiteral)
                    .setParameter(2, vectorLiteral)
                    .setParameter(3, topK);
            case FiltroLecciones.SoloVisibles soloVisibles -> entityManager.createNativeQuery(BUSCAR_SIMILARES_VISIBLES)
                    .setParameter(1, vectorLiteral)
                    .setParameter(2, vectorLiteral)
                    .setParameter(3, topK)
                    .setParameter(4, aLiteralArrayDeTexto(soloVisibles.leccionIds()));
        };
        List<Object[]> filas = query.getResultList();
        return filas.stream()
                .map(fila -> new FragmentoRelevante((String) fila[0], (String) fila[1],
                        ((Number) fila[2]).doubleValue()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ChunkConocimiento save(ChunkConocimiento chunk) {
        entityManager.createNativeQuery(INSERTAR_CHUNK)
                .setParameter(1, chunk.id().value())
                .setParameter(2, chunk.tipoFuente())
                .setParameter(3, chunk.clase())
                .setParameter(4, chunk.documentoId())
                .setParameter(5, chunk.leccionId())
                .setParameter(6, chunk.contenido())
                .setParameter(7, aLiteralVector(chunk.embedding()))
                .setParameter(8, aJson(chunk.metadatos()))
                .setParameter(9, chunk.creadoEn())
                .executeUpdate();
        return chunk;
    }

    /** Formato de literal pgvector: {@code [0.1,0.2,...]} (D-45). */
    private static String aLiteralVector(List<Float> vector) {
        return vector.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * Formato de literal de array de Postgres: {@code {"id1","id2"}}. Un conjunto vacío
     * produce {@code {}}, que {@code ANY(...)} evalúa sin filas sin necesitar un camino
     * especial — a diferencia de un {@code IN (?)} armado con expansión de lista de
     * Hibernate, que sí rompe con una colección vacía (por eso el resto del repo la evita,
     * ver {@code ProgresoLeccionPersistenceAdapter.completadasPorCursoEnLote}). Cada
     * elemento va entre comillas dobles con backslash/comilla escapados: {@code leccionId}
     * es texto libre (clave natural de Skool), no un UUID con alfabeto acotado.
     */
    private static String aLiteralArrayDeTexto(Set<String> valores) {
        return valores.stream()
                .map(PgVectorNativoAdapter::escaparElementoDeArray)
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String escaparElementoDeArray(String valor) {
        return "\"" + valor.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String aJson(Map<String, String> metadatos) {
        try {
            return objectMapper.writeValueAsString(metadatos);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar metadatos a JSON", e);
        }
    }
}
