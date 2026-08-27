package com.renaser.os.users.infrastructure.adapter.in.scheduler;

import com.renaser.os.users.application.ports.in.user.PurgeExpiredAccountsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron diario de baja de cuenta (gap #5): purga (hard delete) las cuentas cuyo plazo de
 * gracia vencio. {@code @EnableScheduling} ya esta declarado globalmente por `points`
 * (D-P4, `PointsSchedulingConfig`) — no hace falta repetirlo aca (mismo criterio que
 * `notifications.PurgaNotificacionesScheduler`/`rocks.VerdugoIgnoradoScheduler`).
 *
 * <p>Horario elegido (04:15 UTC) para no coincidir con `notifications.PurgaNotificacionesScheduler`
 * (04:30) ni con ningun otro cron nocturno ya declarado en el modulo `users`.
 */
@Component
public class PurgarCuentasBajaScheduler {

    private static final Logger log = LoggerFactory.getLogger(PurgarCuentasBajaScheduler.class);

    private final PurgeExpiredAccountsUseCase purgeExpiredAccountsUseCase;

    public PurgarCuentasBajaScheduler(PurgeExpiredAccountsUseCase purgeExpiredAccountsUseCase) {
        this.purgeExpiredAccountsUseCase = purgeExpiredAccountsUseCase;
    }

    @Scheduled(cron = "0 15 4 * * *", zone = "UTC")
    public void purgarVencidas() {
        var resultado = purgeExpiredAccountsUseCase.purgeExpired();
        log.info("[users.PurgarCuentasBajaScheduler] purgadas {} cuenta(s), {} fallida(s)",
                resultado.purgadas(), resultado.fallidas());
    }
}
