package com.renaser.os.habits.domain.model.diario;

import java.util.UUID;

/**
 * Identidad de una entrada de diario (tabla {@code entradas_diario}).
 * Valida y envuelve un UUID, pero <b>no lo genera</b>: la generacion
 * vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code EntradaDiarioId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD 5.4.7: {@code domain/} sin aleatoriedad).
 */
public record EntradaDiarioId(UUID value) {

    public EntradaDiarioId {
        if (value == null) {
            throw new IllegalArgumentException("EntradaDiarioId no puede ser null");
        }
    }

    public static EntradaDiarioId of(UUID value) {
        return new EntradaDiarioId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
