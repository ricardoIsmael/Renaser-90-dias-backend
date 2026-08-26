package com.renaser.os.rocks.domain.model.dashboard;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Una celda de la grilla semanal del dashboard (Hueco #15, {@code weekGrid}
 * del repo viejo, {@code rocks/service.ts:774-788}). {@code completadas}/
 * {@code total} viajan {@code null} para un día futuro (todavía no se vivió,
 * no tiene sentido mostrar "0/3") — mismo criterio que
 * {@code isFuture ? null : ...} del código viejo. {@code total} también es
 * {@code null} cuando ese día no tuvo ninguna Roca Diaria planificada (0 es
 * "falsy" en el código viejo: {@code dayRocks.length || null}).
 */
public record DiaGrillaSemanal(LocalDate fecha, DayOfWeek diaSemana, Integer completadas, Integer total,
                                boolean esHoy) {

    public DiaGrillaSemanal {
        if (fecha == null) {
            throw new IllegalArgumentException("fecha es obligatoria");
        }
        if (diaSemana == null) {
            throw new IllegalArgumentException("diaSemana es obligatorio");
        }
    }
}
