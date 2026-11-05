package com.renaser.os.habits.infrastructure.adapter.in.scheduler;

import com.renaser.os.habits.application.ports.in.carrera.BorrarRutasViejasUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Una vez al día borra las rutas de carreras de más de 180 días (spec 042, FR-042). Diario y a hora fija a propósito: la regla es
 * por el instante en que empezó la carrera, no por el día local de nadie (regla 02 §1 no aplica), y que una ruta viva unas horas de
 * más no lo ve nadie. Si una corrida no ocurre, la siguiente borra lo atrasado (todo se deriva del instante).
 *
 * <p>{@code @SchedulerLock} (C-5): dos instancias borrarían lo mismo dos veces; no hace daño, pero es trabajo doble.
 */
@Component
class BorrarRutasViejasScheduler {

    private static final Logger log = LoggerFactory.getLogger(BorrarRutasViejasScheduler.class);

    private final BorrarRutasViejasUseCase borrar;

    BorrarRutasViejasScheduler(BorrarRutasViejasUseCase borrar) {
        this.borrar = borrar;
    }

    @Scheduled(cron = "0 30 9 * * *", zone = "UTC")
    @SchedulerLock(name = "habits-borrar-rutas-viejas",
            lockAtMostFor = "${renaser.scheduling.shedlock.habits-borrar-rutas-viejas.lock-at-most-for:PT10M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.habits-borrar-rutas-viejas.lock-at-least-for:PT10S}")
    public void ejecutar() {
        int borradas = borrar.borrarRutasViejas();
        if (borradas > 0) {
            log.info("[habits] Rutas de carreras borradas por antigüedad: {}", borradas);
        }
    }
}
