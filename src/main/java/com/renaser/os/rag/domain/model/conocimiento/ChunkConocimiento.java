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
     * Espejo de {@code embedding vector(768)}. D-51 (verificado contra el bytecode del
     * JAR): el modelo por defecto de Spring AI ({@code gemini-embedding-001}) da 3072
     * dimensiones — el adaptador de embeddings DEBE fijar {@code text-embedding-004}
     * (768 nativo) para que esta invariante no reviente en cada indexación.
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
     */
    public static ChunkConocimiento indexar(String tipoFuente, String clase, String documentoId, String leccionId,
                                             String contenido, List<Float> embedding, Map<String, String> metadatos,
                                             Clock clock) {
        Objects.requireNonNull(tipoFuente, "tipoFuente es obligatorio");
        Objects.requireNonNull(contenido, "contenido es obligatorio");
        Objects.requireNonNull(embedding, "embedding es obligatorio");
        requireTipoFuenteNoVacio(tipoFuente);
        requireContenidoNoVacio(contenido);
        requireDimensionCorrecta(embedding);
        return new ChunkConocimiento(ChunkConocimientoId.newId(), tipoFuente, clase, documentoId, leccionId,
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
