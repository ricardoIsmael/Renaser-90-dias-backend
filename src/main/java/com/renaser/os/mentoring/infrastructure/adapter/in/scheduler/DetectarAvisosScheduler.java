package com.renaser.os.mentoring.infrastructure.adapter.in.scheduler;

import com.renaser.os.mentoring.application.ports.in.DetectarAvisosUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Busca condiciones de acompañamiento una vez por hora.
 *
 * <p>Cada hora y no una vez al día porque los participantes están en zonas distintas y "hoy" no
 * empieza al mismo tiempo para todos. Que corra 24 veces no genera 24 avisos: la clave de
 * deduplicación depende del episodio y no de la hora, así que las 23 corridas siguientes no
 * insertan nada.
 *
 * <p>El lock evita el trabajo duplicado entre instancias; la corrección no depende de él.
 */
@Component
class DetectarAvisosScheduler {

    private static final Logger log = LoggerFactory.getLogger(DetectarAvisosScheduler.class);

    private final DetectarAvisosUseCase detectarAvisos;

    DetectarAvisosScheduler(DetectarAvisosUseCase detectarAvisos) {
        this.detectarAvisos = detectarAvisos;
    }

    @Scheduled(cron = "${renaser.scheduling.avisos-acompanamiento.cron:0 50 * * * *}", zone = "UTC")
    @SchedulerLock(name = "mentoring-detectar-avisos",
            lockAtMostFor = "${renaser.scheduling.shedlock.mentoring-detectar-avisos.lock-at-most-for:PT20M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.mentoring-detectar-avisos.lock-at-least-for:PT30S}")
    public void ejecutar() {
        int publicados = detectarAvisos.detectar();
        if (publicados > 0) {
            log.info("[mentoring.DetectarAvisosScheduler] {} aviso(s) de acompanamiento publicado(s)", publicados);
        }
    }
}
