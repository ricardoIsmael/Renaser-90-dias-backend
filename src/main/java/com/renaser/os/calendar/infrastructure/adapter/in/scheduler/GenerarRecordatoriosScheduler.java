package com.renaser.os.calendar.infrastructure.adapter.in.scheduler;

import com.renaser.os.calendar.application.ports.in.recordatorio.GenerarRecordatoriosUseCase;
import com.renaser.os.shared.domain.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reemplaza el cron de 5 minutos del repo viejo (`generar()`, reminderService.ts) que deja
 * en la cola {@code recordatorios_evento} los avisos que faltan para los proximos dias.
 * {@code @EnableScheduling} ya esta declarado globalmente por `points` (D-P4). */
@Component
class GenerarRecordatoriosScheduler {

    private static final Logger log = LoggerFactory.getLogger(GenerarRecordatoriosScheduler.class);

    private final GenerarRecordatoriosUseCase generarUseCase;
    private final Clock clock;

    GenerarRecordatoriosScheduler(GenerarRecordatoriosUseCase generarUseCase, Clock clock) {
        this.generarUseCase = generarUseCase;
        this.clock = clock;
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "UTC")
    void ejecutar() {
        int creados = generarUseCase.generar(clock.now());
        if (creados > 0) {
            log.info("[calendar.GenerarRecordatoriosScheduler] {} recordatorio(s) encolado(s)", creados);
        }
    }
}
