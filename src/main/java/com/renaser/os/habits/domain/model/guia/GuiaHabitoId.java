package com.renaser.os.habits.domain.model.guia;

import java.util.UUID;

public record GuiaHabitoId(UUID value) {

    public GuiaHabitoId {
        if (value == null) {
            throw new IllegalArgumentException("GuiaHabitoId no puede ser null");
        }
    }

    public static GuiaHabitoId of(UUID value) {
        return new GuiaHabitoId(value);
    }

    public static GuiaHabitoId newId() {
        return new GuiaHabitoId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
