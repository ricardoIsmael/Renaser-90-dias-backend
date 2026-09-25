package com.renaser.os.points.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Siete días seguidos del semáforo con su promedio: la ventana VIGENTE (los últimos 7 días
 * cerrados) o una semana sábado→viernes concreta.
 *
 * @param porcentaje   promedio de los días MEDIDO, 1 decimal; null si ninguno lo fue
 * @param color        {@link ColorSemaforo#SIN_DATOS} si {@code porcentaje} es null
 * @param diasConDatos cuántos días entraron al promedio
 * @param cerrada      true solo si es una semana ya cerrada el sábado (su resultado ya no cambia)
 * @param dias         siempre los 7 días, del más viejo al más nuevo
 */
public record VentanaDelSemaforo(LocalDate desde, LocalDate hasta, BigDecimal porcentaje, ColorSemaforo color,
                                 int diasConDatos, boolean cerrada, List<DiaDelSemaforo> dias) {

    public VentanaDelSemaforo {
        Objects.requireNonNull(desde, "desde es obligatorio");
        Objects.requireNonNull(hasta, "hasta es obligatorio");
        Objects.requireNonNull(color, "color es obligatorio");
        dias = dias == null ? List.of() : List.copyOf(dias);
        if ((porcentaje == null) != (color == ColorSemaforo.SIN_DATOS)) {
            throw new IllegalArgumentException("Sin porcentaje es SIN_DATOS, y viceversa: " + porcentaje + "/" + color);
        }
    }
}
