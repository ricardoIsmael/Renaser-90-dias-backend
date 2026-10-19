package com.renaser.os.habits.domain.model.registro;

import java.util.Objects;

/**
 * Cuentas crudas de UN habito de una persona en un rango de fechas, tal como las trae
 * {@code ContarCumplimientoPorHabitoPort}: pura agregacion SQL, sin decision de negocio. Que dias "contaban" lo
 * decide {@link ConteoDiarioHabitos#calificables(int, int)}, la misma regla del semaforo y del ranking.
 *
 * @param nombre                  el que ve la persona: el que ella le puso si lo renombro, o el del catalogo
 * @param totalRegistros          dias del rango en que el habito tuvo registro
 * @param completados             registros en estado COMPLETADO
 * @param opcionalesNoCompletados registros opcionales sin completar (no cuentan, ni a favor ni en contra)
 */
public record CumplimientoDeUnHabito(String nombre, int totalRegistros, int completados,
                                     int opcionalesNoCompletados) {

    public CumplimientoDeUnHabito {
        Objects.requireNonNull(nombre, "nombre es obligatorio");
        if (totalRegistros < 0 || completados < 0 || opcionalesNoCompletados < 0) {
            throw new IllegalArgumentException("Los conteos no pueden ser negativos");
        }
        if (completados + opcionalesNoCompletados > totalRegistros) {
            throw new IllegalArgumentException("completados y opcionales sin completar no pueden pasar del total");
        }
    }

    /** Dias del rango en que el habito contaba. */
    public int calificables() {
        return ConteoDiarioHabitos.calificables(totalRegistros, opcionalesNoCompletados);
    }
}
