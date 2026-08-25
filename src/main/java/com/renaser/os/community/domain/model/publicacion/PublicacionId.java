package com.renaser.os.community.domain.model.publicacion;

import java.util.UUID;

/** Identidad de una publicacion del Muro (tabla `publicaciones_muro`). */
public record PublicacionId(UUID value) {

    public PublicacionId {
        if (value == null) {
            throw new IllegalArgumentException("PublicacionId no puede ser null");
        }
    }

    public static PublicacionId of(UUID value) {
        return new PublicacionId(value);
    }

    public static PublicacionId newId() {
        return new PublicacionId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
