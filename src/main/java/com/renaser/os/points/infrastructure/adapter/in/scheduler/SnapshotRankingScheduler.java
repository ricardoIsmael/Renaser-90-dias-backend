package com.renaser.os.points.infrastructure.adapter.in.scheduler;

import com.renaser.os.points.application.ports.in.ranking.GenerarSnapshotRankingUseCase;
import com.renaser.os.points.domain.model.ranking.TipoRanking;
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

    public SnapshotRankingScheduler(GenerarSnapshotRankingUseCase generarSnapshotRankingUseCase, Clock clock) {
        this.generarSnapshotRankingUseCase = generarSnapshotRankingUseCase;
        this.clock = clock;
    }

    @Scheduled(cron = "0 5 5 * * *", zone = "UTC")
    public void generarSnapshotsDelDia() {
        var hoy = clock.today();
        // GENERAL entra desde D-43: ya existen los tres contratos que lo alimentan
        // (habits/rocks/academy). COHORT sigue afuera — le falta el dato de cohorte.
        for (TipoRanking tipo : new TipoRanking[] {TipoRanking.LEAGUE, TipoRanking.CELL, TipoRanking.GENERAL}) {
            try {
                generarSnapshotRankingUseCase.generar(tipo, hoy);
            } catch (RuntimeException e) {
                // Un tipo que falla no debe tumbar el otro (mismo criterio best-effort que
                log.error("[points.SnapshotRankingScheduler] fallo generando snapshot {} para {}: {}", tipo, hoy,
                        e.getMessage(), e);
            }
        }
    }
}
