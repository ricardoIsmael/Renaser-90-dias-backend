package com.renaser.os.habits.domain.model.espiritu;

import java.util.UUID;

public record RegistroEspirituId(UUID value) {

    public RegistroEspirituId {
        if (value == null) {
            throw new IllegalArgumentException("RegistroEspirituId no puede ser null");
        }
    }

    public static RegistroEspirituId of(UUID value) {
        return new RegistroEspirituId(value);
    }

    public static RegistroEspirituId newId() {
        return new RegistroEspirituId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
