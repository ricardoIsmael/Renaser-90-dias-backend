package com.renaser.os.habits.domain.model.guia;

import java.util.UUID;

/**
 * Identidad de una guia de habito (tabla {@code guias_habito}).
 * Valida y envuelve un UUID, pero <b>no lo genera</b>: la generacion
 * vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code GuiaHabitoId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD 5.4.7: {@code domain/} sin aleatoriedad).
 */
public record GuiaHabitoId(UUID value) {

    public GuiaHabitoId {
        if (value == null) {
            throw new IllegalArgumentException("GuiaHabitoId no puede ser null");
        }
    }

    public static GuiaHabitoId of(UUID value) {
        return new GuiaHabitoId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
