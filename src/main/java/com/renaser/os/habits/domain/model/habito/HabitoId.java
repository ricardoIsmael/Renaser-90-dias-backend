package com.renaser.os.habits.domain.model.habito;

import java.util.UUID;

public record HabitoId(UUID value) {

    public HabitoId {
        if (value == null) {
            throw new IllegalArgumentException("HabitoId no puede ser null");
        }
    }

    public static HabitoId of(UUID value) {
        return new HabitoId(value);
    }

    public static HabitoId newId() {
        return new HabitoId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
