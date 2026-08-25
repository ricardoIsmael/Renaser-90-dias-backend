package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.habits.api.SantuarioRotoEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Escucha {@link SantuarioRotoEvent} — unico de los 4 eventos con match 1:1 exacto contra un
 * valor de {@code TipoNotificacion} ({@code SANTUARIO_ROTO}), sin ambiguedad de mapeo. Ver
 * javadoc de {@link HabitoCompletadoNotificationListener} para el mecanismo de outbox. */
@Component
class SantuarioRotoNotificationListener {

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;

    SantuarioRotoNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
    }

    @ApplicationModuleListener
    void on(SantuarioRotoEvent event) {
        emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(event.participanteId(),
                TipoNotificacion.SANTUARIO_ROTO, "Se rompio tu sesion de Santuario",
                "Tu sesion de Santuario se rompio antes de tiempo.", null));
    }
}
