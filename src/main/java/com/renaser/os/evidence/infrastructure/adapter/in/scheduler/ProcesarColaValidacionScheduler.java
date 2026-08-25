package com.renaser.os.evidence.infrastructure.adapter.in.scheduler;

import com.renaser.os.evidence.application.ports.in.evidencia.ProcesarColaValidacionUseCase;
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
 */
@Component
class ProcesarColaValidacionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProcesarColaValidacionScheduler.class);

    private final ProcesarColaValidacionUseCase procesarColaUseCase;

    ProcesarColaValidacionScheduler(ProcesarColaValidacionUseCase procesarColaUseCase) {
        this.procesarColaUseCase = procesarColaUseCase;
    }

    @Scheduled(cron = "0 * * * * *", zone = "UTC")
    void ejecutar() {
        int procesadas = procesarColaUseCase.procesarLote();
        if (procesadas > 0) {
            log.info("[evidence.ProcesarColaValidacionScheduler] {} evidencia(s) procesada(s)", procesadas);
        }
    }
}
