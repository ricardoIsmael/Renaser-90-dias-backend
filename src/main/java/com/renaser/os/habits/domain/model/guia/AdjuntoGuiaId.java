package com.renaser.os.habits.domain.model.guia;

import java.util.UUID;

public record AdjuntoGuiaId(UUID value) {

    public AdjuntoGuiaId {
        if (value == null) {
            throw new IllegalArgumentException("AdjuntoGuiaId no puede ser null");
        }
    }

    public static AdjuntoGuiaId of(UUID value) {
        return new AdjuntoGuiaId(value);
    }

    public static AdjuntoGuiaId newId() {
        return new AdjuntoGuiaId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
