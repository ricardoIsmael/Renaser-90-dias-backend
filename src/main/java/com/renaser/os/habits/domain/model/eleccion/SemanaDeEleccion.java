package com.renaser.os.habits.domain.model.eleccion;

import java.time.LocalDate;
import java.util.List;

/**
 * Que dias puede elegir el aprendiz para un habito de eleccion semanal: los de ESTA semana de
 * calendario (lunes a domingo, {@code WEEK_ANCHOR=MONDAY} de {@code weeklyChoice.ts}) que todavia no
 * pasaron en SU zona, y ninguno el Dia 0, que es una vista previa.
 *
 * <p>Vive en el dominio (2026-09-23) porque la usan dos caminos que tienen que decir exactamente lo
 * mismo: {@code EleccionDiaSemanalService}, que la hace cumplir al elegir, y
 * {@code PlanDeHabitosService}, que la usa para no ofrecerle al acompanante un dia que el caso de
 * uso despues rechazaria. Antes estaba copiada en los dos.
 */
public final class SemanaDeEleccion {

    private static final int DIAS_DE_LA_SEMANA = 7;

    private SemanaDeEleccion() {
    }

    /** El lunes de la semana de calendario de {@code fecha}. */
    public static LocalDate lunesDe(LocalDate fecha) {
        return fecha.minusDays(fecha.getDayOfWeek().getValue() - 1);
    }

    /** {@code hoy} ya es la fecha en la zona del aprendiz. Vacio el Dia 0. */
    public static List<LocalDate> diasElegibles(int diaPrograma, LocalDate hoy) {
        if (diaPrograma == 0) {
            return List.of();
        }
        return hoy.datesUntil(lunesDe(hoy).plusDays(DIAS_DE_LA_SEMANA)).toList();
    }

    /** Sin mirar el Dia 0: esa guarda tiene su propio mensaje en el caso de uso. */
    public static boolean esElegible(LocalDate fecha, LocalDate hoy) {
        return !fecha.isBefore(hoy) && !fecha.isAfter(lunesDe(hoy).plusDays(DIAS_DE_LA_SEMANA - 1));
    }
}
