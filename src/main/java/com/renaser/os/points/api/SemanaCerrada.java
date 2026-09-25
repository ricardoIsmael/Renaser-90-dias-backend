package com.renaser.os.points.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * La foto de una semana sábado→viernes tal como se cerró (y se reportó) el sábado 00:00. No se
 * reescribe aunque después cambie un día: es lo que se le informó a la persona.
 *
 * @param porcentaje null si la semana no tuvo ningún día con datos
 */
public record SemanaCerrada(LocalDate desde, LocalDate hasta, BigDecimal porcentaje, ColorSemaforo color,
                            int diasConDatos, Instant cerradaEn) {
}
