package com.renaser.os.habits.domain.model.habito;

import java.util.UUID;

/**
 * Identidad de un habito de catalogo o personal (tabla {@code habitos}).
 * Valida y envuelve un UUID, pero <b>no lo genera</b>: la generacion
 * vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code HabitoId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD 5.4.7: {@code domain/} sin aleatoriedad).
 */
public record HabitoId(UUID value) {

    public HabitoId {
        if (value == null) {
            throw new IllegalArgumentException("HabitoId no puede ser null");
        }
    }

    public static HabitoId of(UUID value) {
        return new HabitoId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
