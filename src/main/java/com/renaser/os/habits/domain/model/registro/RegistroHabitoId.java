package com.renaser.os.habits.domain.model.registro;

import java.util.UUID;

/** Identidad de un registro diario de habito (tabla `registros_habito`). */
public record RegistroHabitoId(UUID value) {

    public RegistroHabitoId {
        if (value == null) {
            throw new IllegalArgumentException("RegistroHabitoId no puede ser null");
        }
    }

    public static RegistroHabitoId of(UUID value) {
        return new RegistroHabitoId(value);
    }

    public static RegistroHabitoId newId() {
        return new RegistroHabitoId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
