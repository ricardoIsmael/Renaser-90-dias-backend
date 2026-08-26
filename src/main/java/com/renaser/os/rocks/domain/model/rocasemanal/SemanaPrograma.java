package com.renaser.os.rocks.domain.model.rocasemanal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Semanas de programa (lunes-domingo), con una semana 1 flexible cuando
 * `fechaInicio` no cae en lunes. Portado de `src/features/rocks/week.ts` del
 * repo viejo (`getWeekBoundaries`, `getWeekNumberForDate`).
 */
public final class SemanaPrograma {

    private SemanaPrograma() {
    }

    /** El domingo en o después de {@code fecha} — fin de la semana de programa 1. */
    public static LocalDate primerDomingoDesde(LocalDate fecha) {
        int diasHastaDomingo = (DayOfWeek.SUNDAY.getValue() - fecha.getDayOfWeek().getValue() + 7) % 7;
        return fecha.plusDays(diasHastaDomingo);
    }

    /** El número de semana de programa (1-based) al que pertenece {@code fecha}. */
    public static int numeroSemanaParaFecha(LocalDate fechaInicio, LocalDate fecha) {
        LocalDate primerDomingo = primerDomingoDesde(fechaInicio);
        if (!fecha.isAfter(primerDomingo)) {
            return 1;
        }
        long diasDespuesSemana1 = ChronoUnit.DAYS.between(primerDomingo, fecha);
        return 2 + (int) ((diasDespuesSemana1 - 1) / 7);
    }

    /**
     * Inicio y fin (lunes-domingo) de una semana de programa — semana 1 corta
     * si {@code fechaInicio} no cae lunes (flexible, igual que arriba). Portado
     * de {@code week.ts::getWeekBoundaries} (repo viejo), sin recortar contra
     * el fin del programa — quien llama recorta con {@link #finDelPrograma}
     * si lo necesita (mismo criterio que el repo viejo, `getWeekBoundaries`
     * doc: "Not clipped to the 90-day program end").
     */
    public static LimitesSemana limites(LocalDate fechaInicio, int numeroSemana) {
        LocalDate primerDomingo = primerDomingoDesde(fechaInicio);
        if (numeroSemana <= 1) {
            return new LimitesSemana(fechaInicio, primerDomingo);
        }
        LocalDate inicio = primerDomingo.plusDays(1L + (numeroSemana - 2) * 7L);
        return new LimitesSemana(inicio, inicio.plusDays(6));
    }

    /** Último día del programa de 90 días (día 1 = {@code fechaInicio}). Portado de {@code week.ts::getProgramEndDate}. */
    public static LocalDate finDelPrograma(LocalDate fechaInicio) {
        return fechaInicio.plusDays(89);
    }

    public record LimitesSemana(LocalDate inicio, LocalDate fin) {
    }
}
