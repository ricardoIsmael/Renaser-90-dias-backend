package com.renaser.os.habits.domain.model.carrera;

/**
 * Un kilómetro de una carrera con GPS (spec 042, FR-009): cuántos segundos tardó. El último puede ser parcial: lleva su número
 * igual y sus segundos son los de ese tramo incompleto.
 */
public record Tramo(int km, int segundos) {

    public Tramo {
        if (km < 1) {
            throw new IllegalArgumentException("Los tramos se cuentan desde el km 1.");
        }
        if (segundos <= 0) {
            throw new IllegalArgumentException("Cada tramo tiene que durar más de cero segundos.");
        }
    }

    /** Velocidad del tramo, si fuera un km completo: 3600 / segundos. */
    public double kmPorHora() {
        return 3600.0 / segundos;
    }
}
