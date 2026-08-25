package com.renaser.os.rocks.domain.model.verdugo;

import java.util.UUID;

/** Identidad de un Evento Verdugo (tabla `eventos_verdugo`). */
public record EventoVerdugoId(UUID value) {

    public EventoVerdugoId {
        if (value == null) {
            throw new IllegalArgumentException("EventoVerdugoId no puede ser null");
        }
    }

    public static EventoVerdugoId of(UUID value) {
        return new EventoVerdugoId(value);
    }

    public static EventoVerdugoId newId() {
        return new EventoVerdugoId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
