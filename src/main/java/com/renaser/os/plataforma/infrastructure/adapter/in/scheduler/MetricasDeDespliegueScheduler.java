package com.renaser.os.plataforma.infrastructure.adapter.in.scheduler;

import com.renaser.os.plataforma.application.services.MetricasDeDespliegue;
import jakarta.annotation.PreDestroy;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Las métricas del despliegue (contrato fase 2 §3.3): cada minuto escribe lo contado en memoria; cada hora borra lo de
 * más de 48 h; y al apagar la instancia escribe lo que quedaba, para no perder hasta un minuto de conteos en cada
 * despliegue del backend.
 *
 * <ul>
 *   <li><b>Escribir, sin {@code @SchedulerLock} a propósito</b> (C-5): cada instancia escribe SUS conteos, que solo
 *       ella tiene en memoria; con lock, las demás no escribirían nunca. Es seguro con N instancias porque cada una suma
 *       ({@code ON CONFLICT DO UPDATE SET pedidos = pedidos + …}), no reemplaza.</li>
 *   <li><b>Purgar, con lock</b> ({@code plataforma-purgar-metricas-de-despliegue}): un {@code DELETE … WHERE hora <} es
 *       idempotente, pero no hace falta que lo corran todas.</li>
 * </ul>
 */
@Component
class MetricasDeDespliegueScheduler {

    private static final Logger log = LoggerFactory.getLogger(MetricasDeDespliegueScheduler.class);

    private final MetricasDeDespliegue metricas;

    MetricasDeDespliegueScheduler(MetricasDeDespliegue metricas) {
        this.metricas = metricas;
    }

    @Scheduled(fixedDelayString = "${renaser.plataforma.despliegue.metricas.escribir-cada:PT1M}",
            initialDelayString = "${renaser.plataforma.despliegue.metricas.escribir-cada:PT1M}")
    void escribir() {
        metricas.escribirPendientes();
    }

    @Scheduled(cron = "${renaser.scheduling.plataforma-purgar-metricas-de-despliegue.cron:0 7 * * * *}", zone = "UTC")
    @SchedulerLock(name = "plataforma-purgar-metricas-de-despliegue",
            lockAtMostFor = "${renaser.scheduling.shedlock.plataforma-purgar-metricas-de-despliegue.lock-at-most-for:PT5M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.plataforma-purgar-metricas-de-despliegue.lock-at-least-for:PT30S}")
    void purgar() {
        int borradas = metricas.purgarViejas();
        if (borradas > 0) {
            log.info("[plataforma] metricas del despliegue: {} fila(s) de mas de 48 h borradas", borradas);
        }
    }

    @PreDestroy
    void alApagar() {
        metricas.escribirPendientes();
    }
}
