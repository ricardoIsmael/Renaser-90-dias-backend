package com.renaser.os.notifications.infrastructure.adapter.in.scheduler;

import com.renaser.os.notifications.application.ports.out.whatsapp.EntregasDeWhatsAppPort;
import com.renaser.os.shared.domain.Clock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Retencion de {@code entregas_whatsapp}: 12 meses (spec 041, FR-030). La tabla guarda solo el estado de cada envio (ni el texto, ni el numero), pero no hay motivo para
 * conservarlo mas. Una vez por dia, de madrugada; el trabajo es un {@code DELETE} por fecha, idempotente, y el cerrojo evita que dos instancias lo corran a la vez.
 */
@Component
class PurgaEntregasDeWhatsAppScheduler {

    static final int MESES_DE_RETENCION = 12;
    private static final Logger log = LoggerFactory.getLogger(PurgaEntregasDeWhatsAppScheduler.class);

    private final EntregasDeWhatsAppPort entregas;
    private final Clock clock;

    PurgaEntregasDeWhatsAppScheduler(EntregasDeWhatsAppPort entregas, Clock clock) {
        this.entregas = entregas;
        this.clock = clock;
    }

    @Scheduled(cron = "0 40 4 * * *", zone = "UTC")
    @SchedulerLock(name = "notifications-purgar-entregas-whatsapp",
            lockAtMostFor = "${renaser.scheduling.shedlock.notifications-purgar-entregas-whatsapp.lock-at-most-for:PT10M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.notifications-purgar-entregas-whatsapp.lock-at-least-for:PT30S}")
    void purgar() {
        var limite = ZonedDateTime.ofInstant(clock.now(), ZoneOffset.UTC).minusMonths(MESES_DE_RETENCION).toInstant();
        int purgadas = entregas.purgarAnterioresA(limite);
        log.info("[notifications.PurgaEntregasDeWhatsAppScheduler] purgadas {} entrega(s) de WhatsApp anteriores a {}", purgadas, limite);
    }
}
