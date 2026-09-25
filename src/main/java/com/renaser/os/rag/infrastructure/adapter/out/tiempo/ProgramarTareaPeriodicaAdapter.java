package com.renaser.os.rag.infrastructure.adapter.out.tiempo;

import com.renaser.os.rag.application.ports.out.tiempo.ProgramarTareaPeriodicaPort;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Un {@link ScheduledExecutorService} propio para las tareas de las conversaciones por voz (D-162).
 *
 * <p><b>No se publica como bean</b>, a proposito: si hubiera un bean de tipo
 * {@code ScheduledExecutorService}, Spring Boot dejaria de armar su planificador y todos los
 * {@code @Scheduled} del backend pasarian a correr en este. Queda encerrado aca.
 *
 * <p>Dos hilos alcanzan: cada tarea es una suma en Redis cada cinco segundos por sesion abierta.
 */
@Component
class ProgramarTareaPeriodicaAdapter implements ProgramarTareaPeriodicaPort {

    private static final Logger log = LoggerFactory.getLogger(ProgramarTareaPeriodicaAdapter.class);

    private final ScheduledExecutorService ejecutor = Executors.newScheduledThreadPool(2,
            Thread.ofPlatform().name("voz-en-vivo-", 0).daemon(true).factory());

    @Override
    public TareaProgramada cada(Duration intervalo, Runnable tarea) {
        ScheduledFuture<?> programada = ejecutor.scheduleWithFixedDelay(() -> correr(tarea),
                intervalo.toMillis(), intervalo.toMillis(), TimeUnit.MILLISECONDS);
        return () -> programada.cancel(false);
    }

    /** Una excepcion que escapa cancela la tarea para siempre en silencio; se registra y sigue. */
    private static void correr(Runnable tarea) {
        try {
            tarea.run();
        } catch (RuntimeException e) {
            log.warn("Fallo una tarea periodica de la voz en vivo", e);
        }
    }

    @PreDestroy
    void apagar() {
        ejecutor.shutdownNow();
    }
}
