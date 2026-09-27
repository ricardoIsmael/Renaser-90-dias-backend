package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.DarBienvenidaEnSoporteUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, avisa en el log si {@code BIENVENIDA_REMITENTE_EMAIL} apunta a una cuenta que no puede
 * firmar la bienvenida del soporte (E-330): así se ve en el arranque y no recién cuando entra el
 * primer aprendiz. Nunca frena el arranque.
 */
@Component
class RemitenteDeBienvenidaAlArrancarListener {

    private static final Logger log = LoggerFactory.getLogger(RemitenteDeBienvenidaAlArrancarListener.class);

    private final DarBienvenidaEnSoporteUseCase bienvenida;

    RemitenteDeBienvenidaAlArrancarListener(DarBienvenidaEnSoporteUseCase bienvenida) {
        this.bienvenida = bienvenida;
    }

    @EventListener(ApplicationReadyEvent.class)
    void alArrancar() {
        try {
            bienvenida.revisarRemitenteConfigurado();
        } catch (RuntimeException e) {
            log.warn("[chat.bienvenida] no se pudo revisar el remitente configurado al arrancar: {}", e.toString());
        }
    }
}
