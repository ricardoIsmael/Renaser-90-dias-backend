package com.renaser.os.habits.domain.model.diario;

import java.util.UUID;

public record EntradaDiarioId(UUID value) {

    public EntradaDiarioId {
        if (value == null) {
            throw new IllegalArgumentException("EntradaDiarioId no puede ser null");
        }
    }

    public static EntradaDiarioId of(UUID value) {
        return new EntradaDiarioId(value);
    }

    public static EntradaDiarioId newId() {
        return new EntradaDiarioId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
