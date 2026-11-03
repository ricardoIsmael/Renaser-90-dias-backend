package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.rocks.api.RocaCompletadaEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Escucha {@link RocaCompletadaEvent} de `rocks` — ver javadoc de
 * {@link HabitoCompletadoNotificationListener} para el mecanismo de outbox. */
@Component
class RocaCompletadaNotificationListener {

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;

    RocaCompletadaNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
    }

    @ApplicationModuleListener
    void on(RocaCompletadaEvent event) {
        // C-7: rocaId es la clave de deduplicacion si el outbox reentrega este evento.
        // Issue #62: la app no dice «roca» (son «acciones» de los objetivos) y SER lee este texto con consultar_notificaciones.
        emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(event.participanteId(),
                TipoNotificacion.HITO_PROGRAMA, "Acción cumplida", "Cumpliste una acción de tus objetivos.", null,
                event.rocaId()));
    }
}
