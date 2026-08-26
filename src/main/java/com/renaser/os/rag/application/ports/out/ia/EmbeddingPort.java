package com.renaser.os.rag.application.ports.out.ia;

import java.util.List;

/**
 * Genera el vector de embedding de un texto — usado tanto para indexar conocimiento
 * ({@code ConocimientoService}) como para convertir la consulta de Renasia en un
 * vector antes de buscar en {@code PgVectorNativoAdapter} (D-45: {@code VectorStorePort}
 * recibe texto, no vector; el adaptador de vectorstore llama a este puerto internamente).
 *
 * <p>D-51 (verificado contra el bytecode del JAR): la implementación real DEBE fijar
 * {@code spring.ai.google.genai.embedding.text.model=text-embedding-004} — el modelo
 * por defecto de Spring AI ({@code gemini-embedding-001}) da 3072 dimensiones, no las
 * 768 de la columna {@code vector(768)}.
 *
 * <p><b>SIN IA en este alcance</b>: la única implementación es
 * {@code NoOpEmbeddingAdapter}, que devuelve un vector de ceros de
 * {@value com.renaser.os.rag.domain.model.conocimiento.ChunkConocimiento#DIMENSION_EMBEDDING}
 * posiciones.
 */
public interface EmbeddingPort {

    /** Nunca devuelve {@code null}; la lista tiene exactamente {@code DIMENSION_EMBEDDING} elementos. */
    List<Float> generar(String texto);
}
