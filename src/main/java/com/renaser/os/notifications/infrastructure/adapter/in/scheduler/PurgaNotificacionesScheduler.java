package com.renaser.os.notifications.infrastructure.adapter.in.scheduler;

import com.renaser.os.notifications.application.ports.out.notificacion.SaveNotificacionPort;
import com.renaser.os.notifications.domain.model.notificacion.Notificacion;
import com.renaser.os.shared.domain.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;

/** Retencion (`docs/PLAN_DE_MODULOS.md` §5 "notifications"): purga filas de mas de
 * {@link Notificacion#RETENCION_DIAS} dias. La bandeja YA filtra por esa misma ventana en
 * lectura (`ListarNotificacionesUseCase`), este cron es solo higiene de la tabla — mismo
 * comentario del baseline ("la app ya corta a 90 en lectura", V1__baseline_renaser.sql:1371).
 * {@code @EnableScheduling} ya esta declarado globalmente por `points` (D-P4,
 * `PointsSchedulingConfig`) — no hace falta repetirlo aca (mismo criterio que
 * `rocks.VerdugoIgnoradoScheduler`). */
@Component
public class PurgaNotificacionesScheduler {

    private static final Logger log = LoggerFactory.getLogger(PurgaNotificacionesScheduler.class);

    private final SaveNotificacionPort saveNotificacionPort;
    private final Clock clock;

    public PurgaNotificacionesScheduler(SaveNotificacionPort saveNotificacionPort, Clock clock) {
        this.saveNotificacionPort = saveNotificacionPort;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "UTC")
    public void purgarAntiguas() {
        var limite = clock.now().minus(Notificacion.RETENCION_DIAS, ChronoUnit.DAYS);
        int purgadas = saveNotificacionPort.purgarAnterioresA(limite);
        log.info("[notifications.PurgaNotificacionesScheduler] purgadas {} notificacion(es) anteriores a {}",
                purgadas, limite);
    }
}
