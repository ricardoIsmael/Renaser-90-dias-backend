package com.renaser.os.rag.infrastructure.adapter.out.vectorstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.rag.application.ports.out.conocimiento.SaveChunkConocimientoPort;
import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort;
import com.renaser.os.rag.application.ports.out.ia.EmbeddingPort;
import com.renaser.os.rag.domain.model.conocimiento.ChunkConocimiento;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
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

    @Override
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<FragmentoRelevante> buscarSimilares(String consulta, int topK) {
        String vectorLiteral = aLiteralVector(embeddingPort.generar(consulta));
        List<Object[]> filas = entityManager.createNativeQuery(BUSCAR_SIMILARES)
                .setParameter(1, vectorLiteral)
                .setParameter(2, vectorLiteral)
                .setParameter(3, topK)
                .getResultList();
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

    private String aJson(Map<String, String> metadatos) {
        try {
            return objectMapper.writeValueAsString(metadatos);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar metadatos a JSON", e);
        }
    }
}
