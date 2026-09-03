package com.renaser.os.rag.application.ports.out.ia;

import java.util.List;

/**
 * Genera el vector de embedding de un texto — usado tanto para indexar conocimiento
 * ({@code ConocimientoService}) como para convertir la consulta de Renasia en un
 * vector antes de buscar en {@code PgVectorNativoAdapter} (D-45: {@code VectorStorePort}
 * recibe texto, no vector; el adaptador de vectorstore llama a este puerto internamente).
 *
 * <p><b>D-51 quedó obsoleta (2026-09-03).</b> La decisión original fijaba
 * {@code text-embedding-004} por dar 768 dimensiones nativas, pero Google lo retiró el
 * 2026-01-14. La configuración vigente usa {@code gemini-embedding-001} (el default de
 * Spring AI) con {@code spring.ai.google.genai.embedding.text.dimensions=768} explícito —
 * el modelo trunca vía Matryoshka Representation Learning, no es un recorte casero. Sin esa
 * propiedad, el modelo devuelve 3072 dimensiones, que no calzan con la columna
 * {@code vector(768)}.
 *
 * <p>Implementaciones: {@code NoOpEmbeddingAdapter} (activa mientras
 * {@code renaser.ia.proveedor=noop}, su default), que devuelve un vector de ceros de
 * {@value com.renaser.os.rag.domain.model.conocimiento.ChunkConocimiento#DIMENSION_EMBEDDING}
 * posiciones, y {@code GoogleGenAiEmbeddingAdapter} (activa con
 * {@code renaser.ia.proveedor=google}), que falla explícitamente si el modelo devuelve una
 * cantidad de dimensiones distinta a la esperada en vez de truncar en silencio.
 */
public interface EmbeddingPort {

    /** Nunca devuelve {@code null}; la lista tiene exactamente {@code DIMENSION_EMBEDDING} elementos. */
    List<Float> generar(String texto);
}
