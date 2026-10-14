package com.renaser.os.points.infrastructure.adapter.in.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.renaser.os.points.application.ports.in.ranking.GenerarSnapshotRankingUseCase;
import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.application.ProcesosConInterruptor;
import com.renaser.os.shared.domain.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SnapshotRankingScheduler {

    private static final Logger log = LoggerFactory.getLogger(SnapshotRankingScheduler.class);

    private final GenerarSnapshotRankingUseCase generarSnapshotRankingUseCase;
    private final Clock clock;
    private final Interruptores interruptores;

    public SnapshotRankingScheduler(GenerarSnapshotRankingUseCase generarSnapshotRankingUseCase, Clock clock,
                                    Interruptores interruptores) {
        this.interruptores = interruptores;
        this.generarSnapshotRankingUseCase = generarSnapshotRankingUseCase;
        this.clock = clock;
    }

    @Scheduled(cron = "0 5 5 * * *", zone = "UTC")
    /* Sin cerrojo, dos instancias corren el mismo barrido a la vez. `C-5` lo exige
       para todo @Scheduled que mute estado compartido, y este quedo afuera. */
    @SchedulerLock(name = "points-snapshot-ranking",
            lockAtMostFor = "${renaser.scheduling.shedlock.points-snapshot-ranking.lock-at-most-for:PT10M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.points-snapshot-ranking.lock-at-least-for:PT30S}")
    public void generarSnapshotsDelDia() {
        var hoy = clock.today();
        // GENERAL entra desde D-43, KILOMETROS desde D-226 y GIMNASIO desde D-266. COHORT sigue afuera — le falta el dato
        // de cohorte. La lista es la misma que usa la regeneracion del panel (un solo lugar).
        for (TipoRanking tipo : TipoRanking.CON_CORTE_DIARIO) {
            if (saltaPorSuFuncionalidad(tipo)) {
                continue;
            }
            try {
                generarSnapshotRankingUseCase.generar(tipo, hoy);
            } catch (RuntimeException e) {
                // Un tipo que falla no debe tumbar el otro (mismo criterio best-effort que
                log.error("[points.SnapshotRankingScheduler] fallo generando snapshot {} para {}: {}", tipo, hoy,
                        e.getMessage(), e);
            }
        }
    }

    /**
     * Spec 002: el corte del ranking sigue aunque el ranking esté apagado (es dato), pero el de kilómetros salta con
     * {@code habitos.km} apagado y el de gimnasio con {@code habitos.gimnasio} (columna «Proceso» del catálogo).
     */
    private boolean saltaPorSuFuncionalidad(TipoRanking tipo) {
        String clave = switch (tipo) {
            case KILOMETROS -> "habitos.km";
            case GIMNASIO -> "habitos.gimnasio";
            default -> null;
        };
        return clave != null && ProcesosConInterruptor.saltaEsteTurno(interruptores, clave,
                "points.snapshot-ranking " + tipo);
    }
}
