package com.renaser.os.evidence.infrastructure.adapter.in.scheduler;

import com.renaser.os.evidence.application.ports.in.evidencia.ProcesarColaValidacionUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Consume la cola de validación IA ({@code evidencias_cola_ia_idx}) en lotes de 25, cada
 * minuto — mismo espíritu que {@code calendar.DespacharRecordatoriosScheduler}: la
 * ventana entre "subida" y "primer intento de validación" debe ser corta.
 *
 * <p>SIN IA en este alcance (ver {@code NoOpValidacionIAAdapter}): cada corrida solo
 * incrementa {@code intentosIa} hasta el fallback a {@code REVISION_MANUAL}.
 *
 * <p><b>C-5 (docs/informes/auditoria-fixes/C-5.md): {@code @SchedulerLock}, no opcional.</b>
 * {@code EvidenciaService.pendientesLote} usa {@code FOR UPDATE SKIP LOCKED}, pero desde el
 * arreglo de C-4 ese lock solo dura lo que tarda el SELECT (la transacción que lo sostiene
 * ya no envuelve el procesamiento). Con dos instancias, el lock se libera en milisegundos y
 * la segunda puede tomar el MISMO lote "PENDIENTE" mientras la primera todavía lo está
 * procesando — antes esto era invisible porque la transacción vieja retenía el lock por todo
 * el procesamiento (hasta 19 min con IA real), tapando la ventana. Arreglar C-4 la agrandó;
 * este lock la cierra del todo.
 */
@Component
class ProcesarColaValidacionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProcesarColaValidacionScheduler.class);

    private final ProcesarColaValidacionUseCase procesarColaUseCase;

    ProcesarColaValidacionScheduler(ProcesarColaValidacionUseCase procesarColaUseCase) {
        this.procesarColaUseCase = procesarColaUseCase;
    }

    /**
     * {@code lockAtMostFor} por defecto 20 minutos: el propio javadoc de
     * {@code EvidenciaService.procesarLote} calcula hasta 19 min como peor caso con un lote
     * de 25 evidencias y una IA real calibrada a 45s por evidencia (CLAUDE.MD §7) — 20 min
     * deja margen sin dejar el lock preso demasiado tiempo si el proceso muere a mitad de
     * lote (el cron vuelve a correr al minuto siguiente). Configurable porque es un
     * parámetro de negocio/infraestructura, no un valor fijo (CLAUDE.MD, preferencia del
     * dueño del proyecto sobre parámetros configurables).
     */
    @Scheduled(cron = "0 * * * * *", zone = "UTC")
    @SchedulerLock(name = "evidence-procesar-cola-validacion",
            lockAtMostFor = "${renaser.scheduling.shedlock.evidence-procesar-cola.lock-at-most-for:PT20M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.evidence-procesar-cola.lock-at-least-for:PT10S}")
    public void ejecutar() {
        int procesadas = procesarColaUseCase.procesarLote();
        if (procesadas > 0) {
            log.info("[evidence.ProcesarColaValidacionScheduler] {} evidencia(s) procesada(s)", procesadas);
        }
    }
}
