package com.renaser.os.points.api;

import java.util.Objects;

/**
 * Cómo le fue a una persona con UN hábito en un rango de fechas: cuántos días contaban y cuántos cumplió. Lo
 * reporta {@code habits} al mensaje del semáforo (D-277) para elegir su hábito más flojo y el que mejor le va.
 *
 * <p>Qué cuenta como «contaba» lo decide {@code habits} con la misma regla que ya usa el semáforo
 * ({@code ConteoDiarioHabitos.calificables()}): un opcional no cumplido no entra.
 *
 * @param nombre     el nombre que ve la persona: el que le puso ella si lo renombró, o el del catálogo
 * @param programados días del rango en que el hábito contaba (nunca negativo)
 * @param cumplidos   cuántos de esos se cumplieron (entre 0 y {@code programados})
 */
public record CumplimientoDeHabito(String nombre, int programados, int cumplidos) {

    public CumplimientoDeHabito {
        Objects.requireNonNull(nombre, "nombre es obligatorio");
        if (programados < 0) {
            throw new IllegalArgumentException("programados no puede ser negativo: " + programados);
        }
        if (cumplidos < 0 || cumplidos > programados) {
            throw new IllegalArgumentException("cumplidos fuera de rango: " + cumplidos + "/" + programados);
        }
    }
}
