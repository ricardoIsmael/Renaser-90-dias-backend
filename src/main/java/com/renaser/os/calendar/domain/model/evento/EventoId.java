package com.renaser.os.calendar.domain.model.evento;

import java.util.UUID;

public record EventoId(UUID value) {

    public EventoId {
        if (value == null) {
            throw new IllegalArgumentException("EventoId no puede ser null");
        }
    }

    public static EventoId newId() {
        return new EventoId(UUID.randomUUID());
    }

    public static EventoId of(UUID value) {
        return new EventoId(value);
    }

    public static EventoId of(String value) {
        return new EventoId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
