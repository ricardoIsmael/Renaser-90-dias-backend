package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.habits.api.RachaCompletadaEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Escucha {@link RachaCompletadaEvent} (ciclo de 24h de Racha sin celular completo) — ver
 * javadoc de {@link HabitoCompletadoNotificationListener} para el mecanismo de outbox. */
@Component
class RachaCompletadaNotificationListener {

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;

    RachaCompletadaNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
    }

    @ApplicationModuleListener
    void on(RachaCompletadaEvent event) {
        emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(event.participanteId(),
                TipoNotificacion.LOGRO_DESBLOQUEADO, "Racha sin celular completada",
                "Completaste un ciclo completo de Santuario sin celular.", null));
    }
}
