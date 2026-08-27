package com.renaser.os.notifications.application.services;

import com.renaser.os.points.api.NotificacionesNoLeidasFinder;
import com.renaser.os.notifications.application.ports.out.notificacion.LoadNotificacionPort;
import com.renaser.os.notifications.domain.model.notificacion.Notificacion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Implementa {@link NotificacionesNoLeidasFinder}. Mismo guard de actor y misma ventana de
 * retencion que {@link NotificacionService#listar} — no se duplica la regla, se reutiliza
 * {@link ActorNotificacionesGuard}.
 */
@Service
class NotificacionesNoLeidasService implements NotificacionesNoLeidasFinder {

    private final LoadNotificacionPort loadNotificacionPort;
    private final ActorNotificacionesGuard actorGuard;
    private final Clock clock;

    NotificacionesNoLeidasService(LoadNotificacionPort loadNotificacionPort, ActorNotificacionesGuard actorGuard,
                                   Clock clock) {
        this.loadNotificacionPort = loadNotificacionPort;
        this.actorGuard = actorGuard;
        this.clock = clock;
    }

    @Override
    public long contarNoLeidas(UserId participanteId) {
        actorGuard.requireActivo(participanteId);
        Instant desde = clock.now().minus(Notificacion.RETENCION_DIAS, ChronoUnit.DAYS);
        return loadNotificacionPort.contarNoLeidas(participanteId, desde);
    }
}
