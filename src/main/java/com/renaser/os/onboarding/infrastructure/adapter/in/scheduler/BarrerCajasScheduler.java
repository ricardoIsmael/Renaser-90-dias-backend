package com.renaser.os.onboarding.infrastructure.adapter.in.scheduler;

import com.renaser.os.onboarding.application.ports.in.caja.BarrerCajasUseCase;
import com.renaser.os.onboarding.application.ports.in.caja.BarrerCajasUseCase.ResultadoDelBarrido;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Barrido de la Caja Renaser (D-219). CADA HORA y no a una hora fija: «llegó al Día 8» ocurre a una hora UTC
 * distinta según la zona de cada persona (regla 02 §1, E-91), y el dominio decide con quién hay algo que
 * hacer.
 *
 * <p>Minuto 45: lejos de 05:00–05:05 UTC (medianoche de Lima: expiración de hábitos, generación del día,
 * ranking) y de los minutos 25 y 40 del semáforo.
 *
 * <p>{@code @SchedulerLock} (C-5, fila 13): sin cerrojo, dos instancias recorrerían el padrón a la vez. No
 * duplicarían avisos —la marca va con {@code ON CONFLICT DO NOTHING} y solo quien la inserta avisa—, pero es
 * trabajo doble. El cerrojo evita el desperdicio; la marca, el daño.
 */
@Component
public class BarrerCajasScheduler {

    private static final Logger log = LoggerFactory.getLogger(BarrerCajasScheduler.class);

    private final BarrerCajasUseCase barrerCajas;

    public BarrerCajasScheduler(BarrerCajasUseCase barrerCajas) {
        this.barrerCajas = barrerCajas;
    }

    @Scheduled(cron = "${renaser.scheduling.caja.cron:0 45 * * * *}", zone = "UTC")
    @SchedulerLock(name = "onboarding-barrer-cajas",
            lockAtMostFor = "${renaser.scheduling.shedlock.onboarding-barrer-cajas.lock-at-most-for:PT20M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.onboarding-barrer-cajas.lock-at-least-for:PT30S}")
    public void barrer() {
        ResultadoDelBarrido resultado = barrerCajas.barrer();
        if (resultado.fallidas() > 0) {
            log.warn("[onboarding.BarrerCajasScheduler] {} cajas fallaron (de {}); se reintentan en la proxima corrida",
                    resultado.fallidas(), resultado.evaluadas());
        }
        log.info("[onboarding.BarrerCajasScheduler] evaluadas={} avisos={}", resultado.evaluadas(), resultado.avisos());
    }
}
