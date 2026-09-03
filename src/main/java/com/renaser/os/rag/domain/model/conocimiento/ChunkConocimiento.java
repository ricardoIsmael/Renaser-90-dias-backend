package com.renaser.os.rag.domain.model.conocimiento;

import com.renaser.os.shared.domain.Clock;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Chunk vectorizado de la base de conocimiento (tabla {@code base_conocimiento},
 * docs/MODULO_RAG.md D-45/D-46). Es el fragmento mínimo que Renasia recupera por
 * similitud coseno para armar el contexto de una respuesta. Agregado independiente:
 * identidad propia ({@link ChunkConocimientoId}), sin relación de composición con
 * {@code conversacion}/{@code espejosombra} (CLAUDE.MD sec. 5.1.2 — un agregado por
 * subcarpeta de {@code domain/}).
 *
 * <p>La ingesta la hace un administrador (D-46), nunca el propio aprendiz — la
 * validación de "quién puede indexar" vive en {@code ConocimientoService}, no acá
 * (CLAUDE.MD sec. 5.4.6): esta clase solo protege sus propios invariantes de datos.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class ChunkConocimiento {

    /**
     * Espejo de {@code embedding vector(768)}.
     *
     * <p><b>D-51 quedó obsoleta (2026-09-03).</b> Aquella decisión mandaba fijar
     * {@code text-embedding-004} porque daba 768 dimensiones nativas. Google lo retiró el
     * 2026-01-14, así que ese modelo ya no existe: apuntar ahí habría hecho fallar la primera
     * indexación con credenciales reales.
     *
     * <p>El reemplazo es {@code gemini-embedding-001}, que devuelve 3072 por defecto y se trunca
     * a 768 con {@code spring.ai.google.genai.embedding.text.dimensions} — truncado nativo del
     * modelo, no un recorte casero. Si algún día se cambia de modelo, no alcanza con que el
     * nuevo mida 768: los vectores de dos modelos distintos no son comparables entre sí, así que
     * hay que reindexar todo {@code base_conocimiento}.
     */
    public static final int DIMENSION_EMBEDDING = 768;

    private final ChunkConocimientoId id;
    private final String tipoFuente;
    private final String clase;
    private final String documentoId;
    /** {@code text}, no {@code uuid} — ids estilo Skool, FK a {@code lecciones.id}. Nullable. */
    private final String leccionId;
    private final String contenido;
    private final List<Float> embedding;
    private final Map<String, String> metadatos;
    private final Instant creadoEn;

    /**
     * Indexa un chunk nuevo. {@code clase}, {@code documentoId} y {@code leccionId} son
     * opcionales (una fuente puede no venir de una lección puntual); {@code tipoFuente}
     * y {@code contenido} son obligatorios, y el embedding debe calzar exacto con
     * {@link #DIMENSION_EMBEDDING} — si no, algo aguas arriba (adaptador de embeddings
     * mal configurado) está mal, y es mejor fallar acá que en el INSERT a Postgres.
     *
     * <p>El {@code id} entra por parámetro, no se genera acá: la identidad viene del puerto
     * {@code IdGenerator} que inyecta el caso de uso ({@code ConocimientoService.indexar}).
     * Así {@code indexar} es referencialmente transparente y un test puede fijar el id que
     * espera, en vez de tener que caer a {@link #rehydrate} para lograrlo.
     */
    public static ChunkConocimiento indexar(ChunkConocimientoId id, String tipoFuente, String clase,
                                             String documentoId, String leccionId, String contenido,
                                             List<Float> embedding, Map<String, String> metadatos, Clock clock) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(tipoFuente, "tipoFuente es obligatorio");
        Objects.requireNonNull(contenido, "contenido es obligatorio");
        Objects.requireNonNull(embedding, "embedding es obligatorio");
        requireTipoFuenteNoVacio(tipoFuente);
        requireContenidoNoVacio(contenido);
        requireDimensionCorrecta(embedding);
        return new ChunkConocimiento(id, tipoFuente, clase, documentoId, leccionId,
                contenido, List.copyOf(embedding), metadatos == null ? Map.of() : Map.copyOf(metadatos), clock.now());
    }

    /** Reconstruye desde persistencia — sin volver a validar invariantes de creación (ya pasaron una vez). */
    public static ChunkConocimiento rehydrate(ChunkConocimientoId id, String tipoFuente, String clase,
                                               String documentoId, String leccionId, String contenido,
                                               List<Float> embedding, Map<String, String> metadatos,
                                               Instant creadoEn) {
        return new ChunkConocimiento(id, tipoFuente, clase, documentoId, leccionId, contenido, List.copyOf(embedding),
                metadatos == null ? Map.of() : Map.copyOf(metadatos), creadoEn);
    }

    private static void requireTipoFuenteNoVacio(String tipoFuente) {
        if (tipoFuente.isBlank()) {
            throw new IllegalArgumentException("tipoFuente no puede estar vacio");
        }
    }

    private static void requireContenidoNoVacio(String contenido) {
        if (contenido.isBlank()) {
            throw new IllegalArgumentException("contenido no puede estar vacio");
        }
    }

    private static void requireDimensionCorrecta(List<Float> embedding) {
        if (embedding.size() != DIMENSION_EMBEDDING) {
            throw new IllegalArgumentException(
                    "embedding debe tener " + DIMENSION_EMBEDDING + " dimensiones, tiene " + embedding.size());
        }
    }
}
