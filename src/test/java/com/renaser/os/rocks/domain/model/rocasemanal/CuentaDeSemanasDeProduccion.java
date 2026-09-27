package com.renaser.os.rocks.domain.model.rocasemanal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * La cuenta de semanas que corre en PRODUCCIÓN (backend {@code 7429a09c}, anterior a D-192), copiada tal
 * cual de {@code SemanaPrograma} de ese commit para comparar contra ella (D-203): las
 * {@code rocas_semanales} guardadas en producción tienen el {@code numero_semana} que dio esta cuenta, y
 * para quien no tiene ajuste de día la cuenta nueva tiene que dar el mismo número en las semanas 1 a 13.
 *
 * <p>No se toca: si cambia, deja de ser la referencia. Solo existe en las pruebas.
 */
public final class CuentaDeSemanasDeProduccion {

    private CuentaDeSemanasDeProduccion() {
    }

    /** El domingo en o después de {@code fecha} — fin de la semana de programa 1. */
    public static LocalDate primerDomingoDesde(LocalDate fecha) {
        int diasHastaDomingo = (DayOfWeek.SUNDAY.getValue() - fecha.getDayOfWeek().getValue() + 7) % 7;
        return fecha.plusDays(diasHastaDomingo);
    }

    /** El número de semana de programa (1-based) al que pertenece {@code fecha}; puede dar 14 o más. */
    public static int numeroSemanaParaFecha(LocalDate fechaInicio, LocalDate fecha) {
        LocalDate primerDomingo = primerDomingoDesde(fechaInicio);
        if (!fecha.isAfter(primerDomingo)) {
            return 1;
        }
        long diasDespuesSemana1 = ChronoUnit.DAYS.between(primerDomingo, fecha);
        return 2 + (int) ((diasDespuesSemana1 - 1) / 7);
    }

    /** Lunes a domingo, la 1 desde {@code fechaInicio}; sin recortar contra el fin del programa. */
    public static SemanaPrograma.LimitesSemana limites(LocalDate fechaInicio, int numeroSemana) {
        LocalDate primerDomingo = primerDomingoDesde(fechaInicio);
        if (numeroSemana <= 1) {
            return new SemanaPrograma.LimitesSemana(fechaInicio, primerDomingo);
        }
        LocalDate inicio = primerDomingo.plusDays(1L + (numeroSemana - 2) * 7L);
        return new SemanaPrograma.LimitesSemana(inicio, inicio.plusDays(6));
    }

    /** Último día del programa de 90 días (día 1 = {@code fechaInicio}). */
    public static LocalDate finDelPrograma(LocalDate fechaInicio) {
        return fechaInicio.plusDays(89);
    }

    /** {@code RocaSemanalService.numeroSemanaAPlanificar} de producción: el +1 SOLO el domingo. */
    public static int semanaAPlanificar(LocalDate fechaInicio, LocalDate hoy) {
        int semanaDeHoy = numeroSemanaParaFecha(fechaInicio, hoy);
        return hoy.getDayOfWeek() == DayOfWeek.SUNDAY ? semanaDeHoy + 1 : semanaDeHoy;
    }
}
