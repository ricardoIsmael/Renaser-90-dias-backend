package com.renaser.os.points.api;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Un día del semáforo de una persona, ya resuelto: cuánto contaba, cuánto cumplió y qué
 * porcentaje dio. El porcentaje y el color existen solo si {@code estado == MEDIDO}; en cualquier
 * otro estado {@code porcentaje} es null y {@code color} es {@link ColorSemaforo#SIN_DATOS}.
 *
 * @param porcentaje entero 0..100, redondeado mitad hacia arriba; null si no es MEDIDO
 */
public record DiaDelSemaforo(LocalDate fecha, EstadoDiaSemaforo estado, Integer porcentaje, ColorSemaforo color,
                             int habitosProgramados, int habitosCumplidos,
                             int objetivosProgramados, int objetivosCumplidos) {

    public DiaDelSemaforo {
        Objects.requireNonNull(fecha, "fecha es obligatoria");
        Objects.requireNonNull(estado, "estado es obligatorio");
        Objects.requireNonNull(color, "color es obligatorio");
        if ((estado == EstadoDiaSemaforo.MEDIDO) != (porcentaje != null)) {
            throw new IllegalArgumentException("Solo un dia MEDIDO tiene porcentaje: " + estado + "/" + porcentaje);
        }
    }

    /** Un día sin cálculo propio (pausado, pendiente, sin datos o fuera del programa). */
    public static DiaDelSemaforo sinPorcentaje(LocalDate fecha, EstadoDiaSemaforo estado) {
        return new DiaDelSemaforo(fecha, estado, null, ColorSemaforo.SIN_DATOS, 0, 0, 0, 0);
    }
}
