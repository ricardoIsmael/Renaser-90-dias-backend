package com.renaser.os.mentoring.domain.model.huecos;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Qué semana se puede pedir (contrato de la spec 015): el lunes de la semana en curso de la persona, el de una pasada
 * hasta la de su Día 1, nunca una futura ni una anterior a su primera semana. Sin fecha, la semana en curso.
 *
 * <p>«Hoy» es el de la persona, en SU zona (regla 02): quien llama ya lo calculó.
 */
public final class SemanaPedida {

    private SemanaPedida() {
    }

    /** El lunes de la semana de {@code fecha}. */
    public static LocalDate lunesDe(LocalDate fecha) {
        return fecha.with(DayOfWeek.MONDAY);
    }

    /**
     * @param pedida       el lunes que llegó en la ruta; {@code null} = la semana en curso
     * @param primeraSemana el lunes de la semana de su Día 1; {@code null} si todavía no lo eligió (entonces la única
     *                      semana con algo que mostrar es la en curso)
     * @throws IllegalArgumentException si no es lunes, es futura o es anterior a la primera semana
     */
    public static LocalDate resolver(LocalDate pedida, LocalDate hoy, LocalDate primeraSemana) {
        LocalDate enCurso = lunesDe(hoy);
        if (pedida == null) {
            return enCurso;
        }
        if (pedida.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("La semana se pide por su lunes: " + pedida + " no lo es.");
        }
        if (pedida.isAfter(enCurso)) {
            throw new IllegalArgumentException("Esa semana todavia no llega: la en curso empieza el " + enCurso + ".");
        }
        LocalDate primera = primeraSemana != null ? primeraSemana : enCurso;
        if (pedida.isBefore(primera)) {
            throw new IllegalArgumentException("Esa semana es anterior a la primera de su programa (" + primera + ").");
        }
        return pedida;
    }
}
