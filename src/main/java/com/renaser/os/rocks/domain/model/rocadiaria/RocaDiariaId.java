package com.renaser.os.rocks.domain.model.rocadiaria;

import java.util.UUID;

/** Identidad de una Roca Diaria (tabla `rocas_diarias`). */
public record RocaDiariaId(UUID value) {

    public RocaDiariaId {
        if (value == null) {
            throw new IllegalArgumentException("RocaDiariaId no puede ser null");
        }
    }

    public static RocaDiariaId of(UUID value) {
        return new RocaDiariaId(value);
    }

    public static RocaDiariaId newId() {
        return new RocaDiariaId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
