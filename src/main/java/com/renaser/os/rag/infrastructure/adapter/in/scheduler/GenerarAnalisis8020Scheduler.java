package com.renaser.os.rag.infrastructure.adapter.in.scheduler;

import com.renaser.os.rag.application.ports.in.analisis.GenerarAnalisisPendientesUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Barrido del análisis 80/20 (spec 031, D-306). Corre CADA HORA, al minuto 15 (el Espejo de la Sombra corre al 0, para no apilar dos
 * barridos con IA a la misma hora), y no una vez a una hora UTC fija: el cierre de la semana y del mes es la medianoche de cada persona
 * en SU zona, que cae a un instante UTC distinto según la zona (regla 02 §1, la familia de E-91). El dominio ({@code
 * CalendarioDelAnalisis}) decide a quién le toca; correrlo tarde se pone al día solo dentro de la ventana de puesta al día.
 *
 * <p>{@code @SchedulerLock} no es opcional (C-5): la generación es un check-then-act protegido por el {@code UNIQUE} y por el upsert que
 * no pisa resultados, pero sin cerrojo dos instancias pagarían dos veces la llamada a la IA. {@code lockAtMostFor} de 6 horas, el mismo
 * peor caso del Espejo: cientos de personas a unos segundos por llamada.
 */
@Component
public class GenerarAnalisis8020Scheduler {

    private final GenerarAnalisisPendientesUseCase barrido;

    public GenerarAnalisis8020Scheduler(GenerarAnalisisPendientesUseCase barrido) {
        this.barrido = barrido;
    }

    @Scheduled(cron = "${renaser.scheduling.analisis-80-20.cron:0 15 * * * *}", zone = "UTC")
    @SchedulerLock(name = "rag-generar-analisis-80-20",
            lockAtMostFor = "${renaser.scheduling.shedlock.rag-generar-analisis-80-20.lock-at-most-for:PT6H}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.rag-generar-analisis-80-20.lock-at-least-for:PT1M}")
    public void generarLosQueTocan() {
        barrido.generarLosQueTocan();
    }
}
