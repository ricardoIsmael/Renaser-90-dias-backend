package com.renaser.os.community.domain.model.acompanamiento;

import java.util.UUID;

public record AsignacionId(UUID value) {

    public AsignacionId {
        if (value == null) {
            throw new IllegalArgumentException("AsignacionId no puede ser null");
        }
    }

    public static AsignacionId of(UUID value) {
        return new AsignacionId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
