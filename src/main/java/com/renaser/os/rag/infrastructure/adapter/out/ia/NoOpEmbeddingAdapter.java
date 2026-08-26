package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.ia.EmbeddingPort;
import com.renaser.os.rag.domain.model.conocimiento.ChunkConocimiento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Placeholder mientras no hay credenciales de Gemini (D-39: {@code spring.ai.google.genai.embedding.*}
 * sigue excluido en {@code application.yaml}) — mismo patrón EXACTO que
 * {@code evidence.NoOpEvidenciaValidacionIAAdapter}. Devuelve un vector de ceros de
 * {@link ChunkConocimiento#DIMENSION_EMBEDDING} posiciones, nunca {@code null}, para que
 * el resto del flujo (persistencia, búsqueda) funcione de punta a punta sin IA real.
 *
 * <p><b>Cómo cablear el real (cuando lleguen las credenciales):</b> un
 * {@code GoogleGenAiEmbeddingAdapter} sobre {@code org.springframework.ai.embedding.EmbeddingModel},
 * activo solo cuando exista ese bean ({@code @ConditionalOnBean(EmbeddingModel.class)}, o
 * simplemente quitando las exclusiones de autoconfig de embeddings del {@code application.yaml}
 * una vez cargado {@code GOOGLE_GENAI_API_KEY}). D-51 (verificado contra el bytecode del
 * JAR): el modelo por defecto ({@code gemini-embedding-001}) da 3072 dimensiones y NO
 * calza con la columna {@code vector(768)} — hay que fijar explícitamente
 * {@code spring.ai.google.genai.embedding.text.model=text-embedding-004} (768 nativo,
 * ya declarado en {@code application.yaml} aunque la autoconfig siga excluida).
 */
@Component
public class NoOpEmbeddingAdapter implements EmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpEmbeddingAdapter.class);

    @Override
    public List<Float> generar(String texto) {
        log.warn("EmbeddingPort.generar(...) placeholder: faltan credenciales de IA (D-39). "
                + "Devolviendo vector de ceros de {} dimensiones.", ChunkConocimiento.DIMENSION_EMBEDDING);
        return Collections.nCopies(ChunkConocimiento.DIMENSION_EMBEDDING, 0.0f);
    }
}
