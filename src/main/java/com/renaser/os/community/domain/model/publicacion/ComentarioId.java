package com.renaser.os.community.domain.model.publicacion;

import java.util.UUID;

/**
 * Identidad de un comentario del Muro (tabla `comentarios_muro`). Valida y envuelve un UUID, pero <b>no lo
 * genera</b>: la generacion vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code ComentarioId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD sec. 5.4.7: {@code domain/} es puro, sin aleatoriedad).
 */
public record ComentarioId(UUID value) {

    public ComentarioId {
        if (value == null) {
            throw new IllegalArgumentException("ComentarioId no puede ser null");
        }
    }

    public static ComentarioId of(UUID value) {
        return new ComentarioId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
