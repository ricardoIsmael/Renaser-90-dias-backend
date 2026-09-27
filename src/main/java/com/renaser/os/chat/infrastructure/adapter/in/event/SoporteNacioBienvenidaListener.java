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
 * <p><b>Sí se reintenta.</b> Aunque no sea {@code @ApplicationModuleListener}, Spring Modulith
 * registra en el outbox ({@code event_publication}) todo {@code @TransactionalEventListener}
 * AFTER_COMMIT: si el proceso muere a mitad de camino o el caso de uso lanza, la publicación queda
 * incompleta y se reentrega al reiniciar o a los 5 minutos
 * ({@code EventPublicationMaintenanceScheduler}). Por eso la bienvenida es idempotente
 * ({@code mensajes_bienvenida}, G-2) y un fallo se lanza en vez de tragarse.
 * <blockquote><b>Corregido 2026-09-26 (G-2).</b> Decía «No es {@code @ApplicationModuleListener} a
 * propósito: ese reintenta los eventos que fallan […] Si falla, queda en el log y Operaciones la
 * manda a mano». Era falso: el outbox también guarda y reentrega esta publicación, y sin marca una
 * reentrega mandaba la tarjeta dos veces.</blockquote>
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
