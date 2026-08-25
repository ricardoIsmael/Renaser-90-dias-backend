package com.renaser.os.calendar.infrastructure.adapter.in.scheduler;

import com.renaser.os.calendar.application.ports.in.recordatorio.DespacharRecordatoriosUseCase;
import com.renaser.os.shared.domain.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reemplaza el cron de despacho del repo viejo (`despachar()`, reminderService.ts): toma
 * los avisos vencidos y sin enviar, y publica {@code RecordatorioEventoDebidoEvent} por
 * cada uno — el envio real (push/email) lo decide `notifications`, fuera de este modulo.
 * Corre cada minuto, mas seguido que el generador: la ventana entre "vence" y "se entrega"
 * debe ser corta. */
@Component
class DespacharRecordatoriosScheduler {

    private static final Logger log = LoggerFactory.getLogger(DespacharRecordatoriosScheduler.class);

    private final DespacharRecordatoriosUseCase despacharUseCase;
    private final Clock clock;

    DespacharRecordatoriosScheduler(DespacharRecordatoriosUseCase despacharUseCase, Clock clock) {
        this.despacharUseCase = despacharUseCase;
        this.clock = clock;
    }

    @Scheduled(cron = "0 * * * * *", zone = "UTC")
    void ejecutar() {
        int despachados = despacharUseCase.despachar(clock.now());
        if (despachados > 0) {
            log.info("[calendar.DespacharRecordatoriosScheduler] {} recordatorio(s) despachado(s)", despachados);
        }
    }
}
