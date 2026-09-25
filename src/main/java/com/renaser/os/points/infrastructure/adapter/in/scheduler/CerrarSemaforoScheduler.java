package com.renaser.os.points.infrastructure.adapter.in.scheduler;

import com.renaser.os.points.application.ports.in.semaforo.CerrarSemaforoUseCase;
import com.renaser.os.points.application.ports.in.semaforo.CerrarSemaforoUseCase.ResultadoDelCierre;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Barrido del semáforo del aprendiz (D-168). Corre CADA HORA y no a una hora fija, porque "se
 * cerró el día" y "sábado 00:00" ocurren a una hora UTC distinta según la zona de cada persona
 * (regla 02 §1, E-91): el dominio decide con quién hay algo que hacer.
 *
 * <p>Minuto 25: lejos de 05:00–05:05 UTC (medianoche de Lima), cuando corren la expiración de
 * hábitos, la generación del día y el snapshot del ranking. Así, la corrida de las 00:25 de Lima ya
 * ve los hábitos de ayer expirados.
 *
 * <p>{@code @SchedulerLock} (C-5): con dos instancias sin cerrojo, las dos insertarían la misma foto
 * semanal a la vez; la segunda perdería contra la PK, pero es trabajo duplicado. El cerrojo evita el
 * desperdicio; la PK y el {@code ON CONFLICT} evitan el daño.
 */
@Component
public class CerrarSemaforoScheduler {

    private static final Logger log = LoggerFactory.getLogger(CerrarSemaforoScheduler.class);

    private final CerrarSemaforoUseCase cerrarSemaforo;

    public CerrarSemaforoScheduler(CerrarSemaforoUseCase cerrarSemaforo) {
        this.cerrarSemaforo = cerrarSemaforo;
    }

    @Scheduled(cron = "${renaser.scheduling.semaforo.cron:0 25 * * * *}", zone = "UTC")
    @SchedulerLock(name = "points-cerrar-semaforo",
            lockAtMostFor = "${renaser.scheduling.shedlock.points-cerrar-semaforo.lock-at-most-for:PT20M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.points-cerrar-semaforo.lock-at-least-for:PT30S}")
    public void cerrarPendientes() {
        ResultadoDelCierre resultado = cerrarSemaforo.cerrarPendientes();
        if (resultado.fallidos() > 0) {
            log.warn("[points.CerrarSemaforoScheduler] {} participantes fallaron (de {}); se reintentan en la proxima corrida",
                    resultado.fallidos(), resultado.evaluados());
        }
        log.info("[points.CerrarSemaforoScheduler] evaluados={} diasGuardados={} semanasCerradas={}",
                resultado.evaluados(), resultado.diasGuardados(), resultado.semanasCerradas());
    }
}
