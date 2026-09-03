package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.ia.ChatIAPort;
import com.renaser.os.rag.domain.model.conversacion.EventoRenasia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Placeholder mientras no hay credenciales de Gemini (D-39) — mismo patrón que
 * {@code evidence.NoOpEvidenciaValidacionIAAdapter}. Nombre específico a propósito: dos
 * módulos de este proyecto ya chocaron por registrar un {@code NoOpValidacionIAAdapter}
 * genérico con el mismo nombre simple (los beans de Spring colisionan por nombre de
 * clase) — nunca repetir ese error.
 *
 * <p>El adaptador real es {@code GoogleGenAiRenasiaChatAdapter} (activo con
 * {@code renaser.ia.proveedor=google}), sobre {@code ChatClient...stream().content()}.
 */
@ConditionalOnProperty(name = "renaser.ia.proveedor", havingValue = "noop", matchIfMissing = true)
@Component
public class NoOpRenasiaChatAdapter implements ChatIAPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpRenasiaChatAdapter.class);

    @Override
    public Flux<EventoRenasia> responder(String prompt, List<String> contexto) {
        log.warn("ChatIAPort.responder(...) placeholder: faltan credenciales de IA (D-39).");
        return Flux.just(
                new EventoRenasia.Texto(
                        "Renasia todavia no esta disponible: faltan credenciales de IA por configurar (D-39)."),
                new EventoRenasia.Fin());
    }
}
