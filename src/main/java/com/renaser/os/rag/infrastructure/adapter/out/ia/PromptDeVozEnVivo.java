package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;

/**
 * El prompt de sistema de la voz en vivo (D-162): <b>el mismo del acompanante</b>
 * ({@code prompts/renasia-sistema.st}) con el bloque de modo voz ({@code prompts/modo-voz.st},
 * D-158) al final, exactamente como lo arma {@link GoogleGenAiRenasiaChatAdapter} cuando el canal es
 * {@code VOZ}. Crisis, limites clinicos, atribucion y herramientas: iguales.
 *
 * <p>Lo unico distinto es el contexto de la base de conocimiento: el chat lo busca con cada
 * pregunta, y aca la sesion se abre antes de que la persona diga nada. Va el mismo texto que usa el
 * chat cuando no encontro nada, asi el prompt no afirma un contexto que no hay.
 */
final class PromptDeVozEnVivo {

    private final PromptTemplate acompanante =
            new PromptTemplate(new ClassPathResource(GoogleGenAiRenasiaChatAdapter.RECURSO_PROMPT_ACOMPANANTE));
    private final String modoVoz =
            new PromptTemplate(new ClassPathResource(GoogleGenAiRenasiaChatAdapter.RECURSO_MODO_VOZ)).render();

    String para(SituacionDelAprendiz situacion) {
        String delAgente = acompanante.render(Map.of(
                "contexto", GoogleGenAiRenasiaChatAdapter.formatearContexto(List.of()),
                "situacion", GoogleGenAiRenasiaChatAdapter.formatearSituacion(situacion)));
        return delAgente + "\n\n" + modoVoz;
    }
}
