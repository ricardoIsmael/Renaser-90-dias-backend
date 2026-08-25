package com.renaser.os.rocks.domain.model.rocasemanal;

import java.util.UUID;

/** Identidad de una Roca Semanal (tabla `rocas_semanales`). */
public record RocaSemanalId(UUID value) {

    public RocaSemanalId {
        if (value == null) {
            throw new IllegalArgumentException("RocaSemanalId no puede ser null");
        }
    }

    public static RocaSemanalId of(UUID value) {
        return new RocaSemanalId(value);
    }

    public static RocaSemanalId newId() {
        return new RocaSemanalId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
