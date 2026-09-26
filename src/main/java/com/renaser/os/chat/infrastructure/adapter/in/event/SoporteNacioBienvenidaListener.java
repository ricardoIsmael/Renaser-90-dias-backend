package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.DarBienvenidaEnSoporteUseCase;
import com.renaser.os.chat.domain.model.conversacion.SoporteDeAprendizNacioEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Manda la bienvenida cuando el soporte ya quedó guardado (D-174).
 *
 * <p>Después del commit, para no mandar una bienvenida a un chat que un rollback deshizo, y en otro
 * hilo, para que dibujar y subir la tarjeta no demoren la aprobación de la cuenta.
 *
 * <p>No es {@code @ApplicationModuleListener} a propósito: ese reintenta los eventos que fallan, y
 * reintentar aquí mandaría la bienvenida dos veces si falló después de la primera foto. Si falla,
 * queda en el log y Operaciones la manda a mano, como hasta hoy.
 */
@Component
class SoporteNacioBienvenidaListener {

    private final DarBienvenidaEnSoporteUseCase darBienvenida;

    SoporteNacioBienvenidaListener(DarBienvenidaEnSoporteUseCase darBienvenida) {
        this.darBienvenida = darBienvenida;
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    void on(SoporteDeAprendizNacioEvent event) {
        darBienvenida.darBienvenida(event.soporteId(), event.aprendizId());
    }
}
