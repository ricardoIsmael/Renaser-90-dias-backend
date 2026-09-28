package com.renaser.os.onboarding.domain.model.caja;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Los hábitos de UN día del aprendiz, como los cuenta {@code habits} para el semáforo (D-168): cuántos
 * contaban y cuántos cumplió. La copia en el dominio de la caja existe para no depender de
 * {@code points.api} desde {@code domain/}.
 */
public record DiaDeHabitos(LocalDate fecha, int programados, int cumplidos) {

    public DiaDeHabitos {
        Objects.requireNonNull(fecha, "fecha");
        if (programados < 0 || cumplidos < 0 || cumplidos > programados) {
            throw new IllegalArgumentException("Conteo fuera de rango: " + cumplidos + "/" + programados);
        }
    }
}
