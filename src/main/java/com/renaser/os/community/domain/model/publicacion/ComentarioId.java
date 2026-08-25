package com.renaser.os.community.domain.model.publicacion;

import java.util.UUID;

/** Identidad de un comentario del Muro (tabla `comentarios_muro`). */
public record ComentarioId(UUID value) {

    public ComentarioId {
        if (value == null) {
            throw new IllegalArgumentException("ComentarioId no puede ser null");
        }
    }

    public static ComentarioId of(UUID value) {
        return new ComentarioId(value);
    }

    public static ComentarioId newId() {
        return new ComentarioId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
