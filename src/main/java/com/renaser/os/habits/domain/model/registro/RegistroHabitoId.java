package com.renaser.os.habits.domain.model.registro;

import java.util.UUID;

/**
 * Identidad de un registro diario de habito (tabla {@code registros_habito}).
 * Valida y envuelve un UUID, pero <b>no lo genera</b>: la generacion
 * vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code RegistroHabitoId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD 5.4.7: {@code domain/} sin aleatoriedad).
 */
public record RegistroHabitoId(UUID value) {

    public RegistroHabitoId {
        if (value == null) {
            throw new IllegalArgumentException("RegistroHabitoId no puede ser null");
        }
    }

    public static RegistroHabitoId of(UUID value) {
        return new RegistroHabitoId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
