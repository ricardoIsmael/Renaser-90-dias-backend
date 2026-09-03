package com.renaser.os.habits.domain.model.santuario;

import java.util.UUID;

/**
 * Identidad de una racha "Dia sin celular" (tabla {@code rachas_sin_celular}).
 * Valida y envuelve un UUID, pero <b>no lo genera</b>: la generacion
 * vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code RachaSinCelularId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD 5.4.7: {@code domain/} sin aleatoriedad).
 */
public record RachaSinCelularId(UUID value) {

    public RachaSinCelularId {
        if (value == null) {
            throw new IllegalArgumentException("RachaSinCelularId no puede ser null");
        }
    }

    public static RachaSinCelularId of(UUID value) {
        return new RachaSinCelularId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
