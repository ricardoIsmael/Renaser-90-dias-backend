package com.renaser.os.chat.domain.model.mensaje;

import java.util.UUID;

/** Identidad de un mensaje (tabla `mensajes`). */
public record MensajeId(UUID value) {

    public MensajeId {
        if (value == null) {
            throw new IllegalArgumentException("MensajeId no puede ser null");
        }
    }

    public static MensajeId of(UUID value) {
        return new MensajeId(value);
    }

    public static MensajeId newId() {
        return new MensajeId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
