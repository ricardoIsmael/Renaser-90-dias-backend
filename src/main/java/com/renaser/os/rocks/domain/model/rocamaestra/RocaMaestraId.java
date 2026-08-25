package com.renaser.os.rocks.domain.model.rocamaestra;

import java.util.UUID;

/** Identidad de una Roca Maestra (tabla `rocas_maestras`) — el objetivo de un participante en un eje. */
public record RocaMaestraId(UUID value) {

    public RocaMaestraId {
        if (value == null) {
            throw new IllegalArgumentException("RocaMaestraId no puede ser null");
        }
    }

    public static RocaMaestraId of(UUID value) {
        return new RocaMaestraId(value);
    }

    public static RocaMaestraId newId() {
        return new RocaMaestraId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
