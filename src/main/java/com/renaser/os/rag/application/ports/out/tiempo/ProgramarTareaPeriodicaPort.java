package com.renaser.os.rag.application.ports.out.tiempo;

import java.time.Duration;

/**
 * Correr algo cada tanto mientras dure una conversacion por voz (D-162): cobrar los segundos
 * usados y cortar cuando se acaba la cuota.
 *
 * <p>Es un puerto y no un {@code @Scheduled} porque la tarea es de UNA sesion viva, no un barrido:
 * nace cuando la persona abre el orbe y muere cuando lo cierra. Y es un puerto y no un
 * {@code ScheduledExecutorService} inyectado porque un bean de ese tipo apaga el planificador que
 * Spring Boot arma para todos los {@code @Scheduled} del backend.
 */
public interface ProgramarTareaPeriodicaPort {

    TareaProgramada cada(Duration intervalo, Runnable tarea);

    interface TareaProgramada {
        void cancelar();
    }
}
