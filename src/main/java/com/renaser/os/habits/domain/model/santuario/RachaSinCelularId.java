package com.renaser.os.habits.domain.model.santuario;

import java.util.UUID;

public record RachaSinCelularId(UUID value) {

    public RachaSinCelularId {
        if (value == null) {
            throw new IllegalArgumentException("RachaSinCelularId no puede ser null");
        }
    }

    public static RachaSinCelularId of(UUID value) {
        return new RachaSinCelularId(value);
    }

    public static RachaSinCelularId newId() {
        return new RachaSinCelularId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
