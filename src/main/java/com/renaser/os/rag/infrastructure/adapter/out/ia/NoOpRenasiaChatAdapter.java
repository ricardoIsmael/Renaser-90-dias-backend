package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.ia.ChatIAPort;
import com.renaser.os.rag.domain.model.conversacion.EventoRenasia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Placeholder mientras no hay credenciales de Gemini (D-39) — mismo patrón que
 * {@code evidence.NoOpEvidenciaValidacionIAAdapter}. Nombre específico a propósito: dos
 * módulos de este proyecto ya chocaron por registrar un {@code NoOpValidacionIAAdapter}
 * genérico con el mismo nombre simple (los beans de Spring colisionan por nombre de
 * clase) — nunca repetir ese error.
 *
 * <p>El adaptador real es {@code GoogleGenAiRenasiaChatAdapter} (activo con
 * {@code renaser.ia.proveedor=google}), sobre {@code ChatClient...stream().content()}.
 *
 * <p>D-102: el texto fijo no nombra a ningun agente a proposito. Sirve a los dos, y el nombre
 * visible del acompanante todavia no lo confirmo el dueno — bautizarlo aca seria una tercera
 * copia del nombre que despues hay que corregir.
 */
@ConditionalOnProperty(name = "renaser.ia.proveedor", havingValue = "noop", matchIfMissing = true)
@Component
public class NoOpRenasiaChatAdapter implements ChatIAPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpRenasiaChatAdapter.class);

    static final String TEXTO_PLACEHOLDER =
            "El asistente todavia no esta disponible: faltan credenciales de IA por configurar (D-39).";

    /**
     * Recibe las herramientas del agente y no las usa: no hay ningun modelo que pueda pedirlas.
     * Se loguea CUANTAS llegaron (nunca su contenido) para dejar constancia de que el cableado
     * esta hecho — es la unica evidencia en ejecucion de que enchufar el proveedor real va a ser
     * configuracion y no reescritura.
     */
    @Override
    public Flux<EventoRenasia> responder(Consulta consulta) {
        log.warn("ChatIAPort.responder(...) placeholder para {} con {} herramienta(s) disponible(s): "
                + "faltan credenciales de IA (D-39).", consulta.agente(), consulta.herramientas().size());
        return Flux.just(new EventoRenasia.Texto(TEXTO_PLACEHOLDER), new EventoRenasia.Fin());
    }
}
