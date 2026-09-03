package com.renaser.os.community.domain.model.celula;

import java.util.UUID;

/**
 * Identidad de una celula (tabla `celulas`). Valida y envuelve un UUID, pero <b>no lo
 * genera</b>: la generacion vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code CelulaId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD sec. 5.4.7: {@code domain/} es puro, sin aleatoriedad).
 */
public record CelulaId(UUID value) {

    public CelulaId {
        if (value == null) {
            throw new IllegalArgumentException("CelulaId no puede ser null");
        }
    }

    public static CelulaId of(UUID value) {
        return new CelulaId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
