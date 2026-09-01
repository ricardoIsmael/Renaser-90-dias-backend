package com.renaser.os.community.domain.model.publicacion;

import java.util.UUID;

/**
 * Identidad de una publicacion del Muro (tabla `publicaciones_muro`). Valida y envuelve un UUID, pero <b>no lo
 * genera</b>: la generacion vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code PublicacionId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD sec. 5.4.7: {@code domain/} es puro, sin aleatoriedad).
 */
public record PublicacionId(UUID value) {

    public PublicacionId {
        if (value == null) {
            throw new IllegalArgumentException("PublicacionId no puede ser null");
        }
    }

    public static PublicacionId of(UUID value) {
        return new PublicacionId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
