package com.renaser.os.rag.infrastructure.adapter.in.scheduler;

import com.renaser.os.rag.application.services.circuito.CopiaDelCircuito;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lee el circuito de la IA al arrancar y lo relee cada 10 s: el respaldo por si un aviso de Redis no llegó.
 *
 * <p><b>Sin {@code @SchedulerLock}, a propósito</b> (tabla de C-5), igual que {@code RecargarEstadosScheduler} de
 * {@code plataforma}: no es un trabajo que tenga que hacer UNA instancia, es la copia en memoria de CADA una. Son dos
 * lecturas chicas de Redis por instancia cada 10 s, y no escribe nada.
 */
@Component
class RecargarCircuitoScheduler {

    private final CopiaDelCircuito copia;

    RecargarCircuitoScheduler(CopiaDelCircuito copia) {
        this.copia = copia;
    }

    @EventListener(ApplicationReadyEvent.class)
    void alArrancar() {
        copia.recargar();
    }

    @Scheduled(fixedDelayString = "${renaser.ia.circuito.recarga-cada:PT10S}",
            initialDelayString = "${renaser.ia.circuito.recarga-cada:PT10S}")
    void recargar() {
        copia.recargar();
    }
}
