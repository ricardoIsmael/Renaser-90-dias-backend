package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.habits.api.HabitoCompletadoEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Primer consumidor real del outbox de Spring Modulith (docs/MODULO_NOTIFICATIONS.md §0):
 * escucha {@link HabitoCompletadoEvent}, publicado por `habits` DENTRO de la misma transaccion
 * que completa el registro (`habits` ya actualiza `points` de forma sincrona, este evento es
 * solo para consumidores eventuales — ver `docs/MODULO_HABITS.md` §4).
 *
 * <p>{@code @ApplicationModuleListener} (no {@code @EventListener} a secas) es el que activa el
 * mecanismo de outbox + reintento de Modulith: corre en su propia transaccion, async, DESPUES
 * de que la transaccion que publico el evento haya hecho commit.
 *
 * <p><b>DN-1 (pregunta abierta, docs/MODULO_NOTIFICATIONS.md §5):</b> el repo viejo NUNCA
 * notificaba la finalizacion de un habito (solo tickets/chat/personal-habits/post-program lo
 * hacian, ver paso 0). No hay tipo de {@code TipoNotificacion} especifico para "habito
 * completado" en el baseline — se usa {@code LOGRO_DESBLOQUEADO} como aproximacion razonable,
 * NO confirmada por negocio. Riesgo real: esto emite una notificacion por CADA habito
 * completado (varias por dia), lo cual puede ser ruido — a revisar.
 */
@Component
class HabitoCompletadoNotificationListener {

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;

    HabitoCompletadoNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
    }

    @ApplicationModuleListener
    void on(HabitoCompletadoEvent event) {
        // C-7: registroId es la clave de deduplicacion si el outbox reentrega este evento.
        emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(event.participanteId(),
                TipoNotificacion.LOGRO_DESBLOQUEADO, "Habito completado",
                "Sumaste " + event.puntosOtorgados() + " puntos.", null, event.registroId()));
    }
}
