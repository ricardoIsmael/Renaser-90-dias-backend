package com.renaser.os.mentoring.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cierre semanal del semáforo de todos los grupos juntos, para el líder de mentores, admin y
 * alquimista: cuántos grupos y cuántos aprendices en cada color, sin nombres (RL-07 del SDD 002;
 * D-168).
 *
 * @param claveDeduplicacion {@code UUID.nameUUIDFromBytes("semaforo-general:" + hasta)}
 */
public record ResumenSemanalGeneralEvent(UUID claveDeduplicacion, LocalDate desde, LocalDate hasta, int grupos,
                                         int verde, int amarillo, int rojo, int sinDatos, int total,
                                         Instant generadoEn) {

    /** Ruta profunda al resumen por grupos. */
    public String rutaApp() {
        return "/semaforo/grupos";
    }
}
