package com.renaser.os.habits.domain.model.espiritu;

import java.util.UUID;

/**
 * Identidad de una entrega de Espiritu del dia (tabla {@code registros_espiritu}).
 * Valida y envuelve un UUID, pero <b>no lo genera</b>: la generacion
 * vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code RegistroEspirituId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD 5.4.7: {@code domain/} sin aleatoriedad).
 */
public record RegistroEspirituId(UUID value) {

    public RegistroEspirituId {
        if (value == null) {
            throw new IllegalArgumentException("RegistroEspirituId no puede ser null");
        }
    }

    public static RegistroEspirituId of(UUID value) {
        return new RegistroEspirituId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
