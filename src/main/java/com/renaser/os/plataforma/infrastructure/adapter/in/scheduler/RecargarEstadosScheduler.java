package com.renaser.os.plataforma.infrastructure.adapter.in.scheduler;

import com.renaser.os.plataforma.application.services.CopiaDeEstados;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lee el estado al arrancar y lo relee cada 30 s (plan.md D3): el respaldo por si un aviso de Redis no llegó.
 *
 * <p><b>Sin {@code @SchedulerLock}, a propósito</b> (tabla de C-5): no es un trabajo que tenga que hacer UNA instancia,
 * es la copia en memoria de CADA una. Con lock, solo una se pondría al día y las demás seguirían con su copia vieja
 * hasta el próximo aviso. Son dos consultas chicas por instancia cada 30 s.
 */
@Component
class RecargarEstadosScheduler {

    private final CopiaDeEstados copia;

    RecargarEstadosScheduler(CopiaDeEstados copia) {
        this.copia = copia;
    }

    @EventListener(ApplicationReadyEvent.class)
    void alArrancar() {
        copia.recargar();
    }

    @Scheduled(fixedDelayString = "${renaser.plataforma.recarga-cada:PT30S}",
            initialDelayString = "${renaser.plataforma.recarga-cada:PT30S}")
    void recargar() {
        copia.recargar();
    }
}
