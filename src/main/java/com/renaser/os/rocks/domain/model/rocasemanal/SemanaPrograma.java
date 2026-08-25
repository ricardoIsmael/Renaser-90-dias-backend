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
}
