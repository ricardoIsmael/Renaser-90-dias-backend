package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.ia.EmbeddingPort;
import com.renaser.os.rag.domain.model.conocimiento.ChunkConocimiento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * <p>El adaptador real es {@code GoogleGenAiEmbeddingAdapter} (activo con
 * {@code renaser.ia.proveedor=google}), sobre {@code org.springframework.ai.embedding.EmbeddingModel}.
 * D-51 quedó obsoleta (2026-01-14, Google retiró {@code text-embedding-004}): la
 * configuración vigente usa el default {@code gemini-embedding-001} con
 * {@code spring.ai.google.genai.embedding.text.dimensions=768} explícito (truncado
 * Matryoshka), ya declarado en {@code application.yaml}.
 */
/** Activo mientras `renaser.ia.proveedor` sea `noop` (su default). Ver application.yaml. */
@ConditionalOnProperty(name = "renaser.ia.proveedor", havingValue = "noop", matchIfMissing = true)
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
