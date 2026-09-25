package com.renaser.os.points.api;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Lo que un módulo dueño (hábitos u objetivos) le reporta al semáforo sobre UN día de UN
 * participante: cuántos elementos contaban ese día y cuántos se cumplieron.
 *
 * <p>Qué "cuenta" lo decide el dueño, no el semáforo: {@code habits} ya descuenta los
 * opcionales no cumplidos ({@code ConteoDiarioHabitos.calificables()}), y en {@code rocks} toda
 * roca planificada cuenta. El semáforo solo suma lo que recibe (D-168,
 * docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §1).
 *
 * @param programados elementos que contaban ese día (nunca negativo)
 * @param cumplidos   cuántos de esos se cumplieron (entre 0 y {@code programados})
 */
public record ConteoDelDia(LocalDate fecha, int programados, int cumplidos) {

    public ConteoDelDia {
        Objects.requireNonNull(fecha, "fecha es obligatoria");
        if (programados < 0) {
            throw new IllegalArgumentException("programados no puede ser negativo: " + programados);
        }
        if (cumplidos < 0 || cumplidos > programados) {
            throw new IllegalArgumentException("cumplidos fuera de rango: " + cumplidos + "/" + programados);
        }
    }
}
