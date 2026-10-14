package com.renaser.os.plataforma.infrastructure.adapter.in.scheduler;

import com.renaser.os.plataforma.application.services.CopiaDeDespliegues;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lee los despliegues al arrancar y los relee cada 30 s (contrato fase 2 §3.2): el respaldo por si un aviso de Redis no
 * llegó. Mismo intervalo que los interruptores ({@code renaser.plataforma.recarga-cada}).
 *
 * <p><b>Sin {@code @SchedulerLock}, a propósito</b> (C-5, como {@link RecargarEstadosScheduler}): es la copia en memoria
 * de CADA instancia; con lock, solo una se pondría al día.
 */
@Component
class RecargarDesplieguesScheduler {

    private final CopiaDeDespliegues copia;

    RecargarDesplieguesScheduler(CopiaDeDespliegues copia) {
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
