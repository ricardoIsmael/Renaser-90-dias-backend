package com.renaser.os.rocks.infrastructure.adapter.in.scheduler;

import com.renaser.os.rocks.application.ports.in.verdugo.ResolverEventosIgnoradosUseCase;
import com.renaser.os.shared.domain.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 23:55 — barrido de Eventos Verdugo sin resolver del día (ver
 * `ResolverEventosIgnoradosUseCase`). `@EnableScheduling` ya está declarado
 * globalmente por `points` (D-P4, `PointsSchedulingConfig`) — no hace falta
 * repetirlo acá.
 */
@Component
public class VerdugoIgnoradoScheduler {

    private final ResolverEventosIgnoradosUseCase resolverUseCase;
    private final Clock clock;

    public VerdugoIgnoradoScheduler(ResolverEventosIgnoradosUseCase resolverUseCase, Clock clock) {
        this.resolverUseCase = resolverUseCase;
        this.clock = clock;
    }

    @Scheduled(cron = "0 55 23 * * *", zone = "UTC")
    public void resolverPendientesDeHoy() {
        resolverUseCase.resolverPendientesDe(clock.today());
    }
}
