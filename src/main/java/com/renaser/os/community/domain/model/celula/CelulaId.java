package com.renaser.os.community.domain.model.celula;

import java.util.UUID;

/** Identidad de una celula (tabla `celulas`). */
public record CelulaId(UUID value) {

    public CelulaId {
        if (value == null) {
            throw new IllegalArgumentException("CelulaId no puede ser null");
        }
    }

    public static CelulaId of(UUID value) {
        return new CelulaId(value);
    }

    public static CelulaId newId() {
        return new CelulaId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
