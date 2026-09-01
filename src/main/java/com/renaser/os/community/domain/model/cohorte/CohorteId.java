package com.renaser.os.community.domain.model.cohorte;

import java.util.UUID;

/**
 * Identidad de una cohorte (tabla `cohortes`). Valida y envuelve un UUID, pero <b>no lo
 * genera</b>: la generacion vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code CohorteId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD sec. 5.4.7: {@code domain/} es puro, sin aleatoriedad).
 */
public record CohorteId(UUID value) {

    public CohorteId {
        if (value == null) {
            throw new IllegalArgumentException("CohorteId no puede ser null");
        }
    }

    public static CohorteId of(UUID value) {
        return new CohorteId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
