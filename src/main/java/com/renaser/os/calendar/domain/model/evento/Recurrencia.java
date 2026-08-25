package com.renaser.os.calendar.domain.model.evento;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 0..1 por evento (tabla {@code recurrencias_evento}). {@code diasSemana} solo tiene
 * sentido con {@code frecuencia = SEMANAL} — vacio significa "cada N semanas desde
 * {@code Evento.iniciaEn}", tal como el repo viejo trata {@code byWeekday: []}.
 *
 * <p>{@code diasSemana} usa {@link DayOfWeek} (ISO 1=lunes..7=domingo) — EXACTAMENTE la
 * convencion de {@code WEEKDAY} en eventTypes.ts (repo viejo) y del wire de la app
 * instalada. La tabla Postgres {@code dias_semana_recurrencia} usa 0=domingo..6=sabado
 * (comentario del baseline); la traduccion vive solo en el adaptador de persistencia.
 */
public record Recurrencia(FrecuenciaRecurrencia frecuencia, int intervalo, Instant hasta, Integer repeticiones,
                           Set<DayOfWeek> diasSemana) {

    public Recurrencia {
        Objects.requireNonNull(frecuencia, "frecuencia es obligatoria");
        if (intervalo < 1) {
            throw new IllegalArgumentException("intervalo debe ser >= 1");
        }
        if (hasta != null && repeticiones != null) {
            throw new IllegalArgumentException("fin_no_contradictorio: usa hasta o repeticiones, no ambos");
        }
        if (repeticiones != null && repeticiones <= 0) {
            throw new IllegalArgumentException("repeticiones debe ser positivo");
        }
        diasSemana = diasSemana == null || diasSemana.isEmpty()
                ? EnumSet.noneOf(DayOfWeek.class)
                : EnumSet.copyOf(diasSemana);
        if (!diasSemana.isEmpty() && frecuencia != FrecuenciaRecurrencia.SEMANAL) {
            throw new IllegalArgumentException("diasSemana solo aplica a SEMANAL");
        }
    }
}
