package com.renaser.os.mentoring.infrastructure.adapter.in.scheduler;

import com.renaser.os.mentoring.application.ports.in.ResumirSemanaDelSemaforoUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Manda el resumen semanal del semáforo (D-168) cuando cada grupo cierra su semana.
 *
 * <p><b>Cada hora y no "el sábado a tal hora UTC"</b>: la semana cierra el sábado 00:00 en la zona
 * de cada grupo, y esa medianoche no cae a una hora UTC fija (regla 02 §1). Quien decide si a un
 * grupo le toca es el dominio ({@code ReglasDelResumenSemanal}); una corrida que no cae en la
 * ventana de ningún grupo no consulta nada más que la lista de grupos.
 *
 * <p>En el minuto 40: el cierre de {@code points} ({@code points-cerrar-semaforo}) corre en el 25.
 * El orden entre crons NO es lo que protege el resultado —si el cierre viene atrasado, el servicio
 * espera a ver alguna semana cerrada antes de avisar—, pero en el caso normal el resumen sale en la
 * primera corrida de la ventana.
 *
 * <p>El lock evita el trabajo doble entre instancias; la corrección no depende de él (la
 * deduplicación por {@code origen_evento_id} impide la notificación doble).
 */
@Component
class ResumirSemanaDelSemaforoScheduler {

    private static final Logger log = LoggerFactory.getLogger(ResumirSemanaDelSemaforoScheduler.class);

    private final ResumirSemanaDelSemaforoUseCase resumirSemana;

    ResumirSemanaDelSemaforoScheduler(ResumirSemanaDelSemaforoUseCase resumirSemana) {
        this.resumirSemana = resumirSemana;
    }

    @Scheduled(cron = "${renaser.scheduling.resumen-semaforo.cron:0 40 * * * *}", zone = "UTC")
    @SchedulerLock(name = "mentoring-resumir-semana-semaforo",
            lockAtMostFor = "${renaser.scheduling.shedlock.mentoring-resumir-semana-semaforo.lock-at-most-for:PT20M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.mentoring-resumir-semana-semaforo.lock-at-least-for:PT30S}")
    public void ejecutar() {
        int publicados = resumirSemana.resumir();
        if (publicados > 0) {
            log.info("[mentoring.ResumirSemanaDelSemaforoScheduler] {} resumen(es) semanal(es) de grupo publicado(s)",
                    publicados);
        }
    }
}
