package com.renaser.os.users.infrastructure.adapter.in.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.renaser.os.users.application.ports.in.user.PurgeExpiredAccountsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Barrido que borra para siempre las cuentas cerradas cuya gracia (30 dias) vencio (D-243).
 * {@code @EnableScheduling} ya esta declarado globalmente por `points` (D-P4).
 *
 * <p><b>Cada hora</b> (minuto 30, libre entre los barridos horarios) y no una vez al dia: la gracia se
 * cuenta en instantes desde el cierre, asi que la cuenta se borra dentro de la hora en que vence, y
 * una corrida perdida la recupera la siguiente sin acumular nada (regla 02, derivar y no incrementar).
 *
 * <blockquote><b>Corregido 2026-10-02 (D-243).</b> Corria una vez al dia a las 04:15 UTC con una gracia
 * de 14 dias en la que la persona conservaba el acceso. El dueño decidio 30 dias con la cuenta
 * cerrada desde el primer momento.</blockquote>
 */
@Component
public class PurgarCuentasBajaScheduler {

    private static final Logger log = LoggerFactory.getLogger(PurgarCuentasBajaScheduler.class);

    private final PurgeExpiredAccountsUseCase purgeExpiredAccountsUseCase;

    public PurgarCuentasBajaScheduler(PurgeExpiredAccountsUseCase purgeExpiredAccountsUseCase) {
        this.purgeExpiredAccountsUseCase = purgeExpiredAccountsUseCase;
    }

    @Scheduled(cron = "${renaser.scheduling.cuentas-cerradas.cron:0 30 * * * *}", zone = "UTC")
    /* Sin cerrojo, dos instancias borrarian la misma cuenta a la vez (C-5). */
    @SchedulerLock(name = "users-purgar-cuentas-baja",
            lockAtMostFor = "${renaser.scheduling.shedlock.users-purgar-cuentas-baja.lock-at-most-for:PT15M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.users-purgar-cuentas-baja.lock-at-least-for:PT30S}")
    public void purgarVencidas() {
        var resultado = purgeExpiredAccountsUseCase.purgeExpired();
        if (resultado.purgadas() > 0 || resultado.fallidas() > 0) {
            log.info("[users.PurgarCuentasBajaScheduler] borradas {} cuenta(s), {} fallida(s)",
                    resultado.purgadas(), resultado.fallidas());
        }
    }
}
