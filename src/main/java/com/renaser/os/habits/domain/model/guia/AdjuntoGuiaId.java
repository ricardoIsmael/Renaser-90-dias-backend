package com.renaser.os.habits.domain.model.guia;

import java.util.UUID;

/**
 * Identidad de un adjunto de guia (tabla {@code adjuntos_guia}).
 * Valida y envuelve un UUID, pero <b>no lo genera</b>: la generacion
 * vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code AdjuntoGuiaId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD 5.4.7: {@code domain/} sin aleatoriedad).
 */
public record AdjuntoGuiaId(UUID value) {

    public AdjuntoGuiaId {
        if (value == null) {
            throw new IllegalArgumentException("AdjuntoGuiaId no puede ser null");
        }
    }

    public static AdjuntoGuiaId of(UUID value) {
        return new AdjuntoGuiaId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
