package com.renaser.os.community.domain.model.cohorte;

import java.util.UUID;

/** Identidad de una cohorte (tabla `cohortes`). */
public record CohorteId(UUID value) {

    public CohorteId {
        if (value == null) {
            throw new IllegalArgumentException("CohorteId no puede ser null");
        }
    }

    public static CohorteId of(UUID value) {
        return new CohorteId(value);
    }

    public static CohorteId newId() {
        return new CohorteId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
