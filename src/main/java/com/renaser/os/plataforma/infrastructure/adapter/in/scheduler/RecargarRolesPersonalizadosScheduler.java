package com.renaser.os.plataforma.infrastructure.adapter.in.scheduler;

import com.renaser.os.plataforma.application.services.CopiaDeRolesPersonalizados;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lee los roles personalizados al arrancar y los relee cada 30 s (contrato de la fase 2, §4.4; D-272): el respaldo por
 * si un aviso no llegó. Sin {@code @SchedulerLock}, como {@link RecargarEstadosScheduler}: es la copia de CADA
 * instancia, no un trabajo de una sola.
 */
@Component
class RecargarRolesPersonalizadosScheduler {

    private final CopiaDeRolesPersonalizados copia;

    RecargarRolesPersonalizadosScheduler(CopiaDeRolesPersonalizados copia) {
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
