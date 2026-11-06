package com.renaser.os.calendar.infrastructure.adapter.in.scheduler;

import com.renaser.os.calendar.application.ports.in.meet.TranscripcionesDeMeetUseCase;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.application.ProcesosConInterruptor;
import com.renaser.os.shared.domain.Clock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Spec 047. Cada 5 minutos busca las transcripciones de las sesiones que terminaron, solo con {@code eventos.meet} y
 * {@code eventos.meet.transcripcion} prendidas (FR-001, FR-002). La retención corre todos los días AUNQUE el interruptor esté apagado: lo que ya se
 * guardó se borra a tiempo igual (FR-014).
 */
@Component
class TranscripcionesDeMeetScheduler {

    static final String CLAVE_DE_MEET = "eventos.meet";
    static final String CLAVE = "eventos.meet.transcripcion";

    private final TranscripcionesDeMeetUseCase useCase;
    private final Interruptores interruptores;
    private final Clock clock;

    TranscripcionesDeMeetScheduler(TranscripcionesDeMeetUseCase useCase, Interruptores interruptores, Clock clock) {
        this.useCase = useCase;
        this.interruptores = interruptores;
        this.clock = clock;
    }

    @Scheduled(cron = "${renaser.scheduling.transcripciones-de-meet.cron:0 */5 * * * *}", zone = "UTC")
    @SchedulerLock(name = "calendar-transcripciones-de-meet",
            lockAtMostFor = "${renaser.scheduling.shedlock.calendar-transcripciones-de-meet.lock-at-most-for:PT10M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.calendar-transcripciones-de-meet.lock-at-least-for:PT30S}")
    void buscar() {
        if (ProcesosConInterruptor.saltaEsteTurno(interruptores, CLAVE_DE_MEET, "calendar.transcripciones-de-meet")
                || ProcesosConInterruptor.saltaEsteTurno(interruptores, CLAVE, "calendar.transcripciones-de-meet")) {
            return;
        }
        useCase.buscarPendientes(clock.now());
    }

    @Scheduled(cron = "${renaser.scheduling.transcripciones-de-meet-retencion.cron:0 30 8 * * *}", zone = "UTC")
    @SchedulerLock(name = "calendar-transcripciones-de-meet-retencion",
            lockAtMostFor = "${renaser.scheduling.shedlock.calendar-transcripciones-de-meet-retencion.lock-at-most-for:PT30M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.calendar-transcripciones-de-meet-retencion.lock-at-least-for:PT1M}")
    void retener() {
        useCase.aplicarRetencion(clock.now());
    }
}
