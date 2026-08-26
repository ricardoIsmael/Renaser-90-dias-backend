package com.renaser.os.rag.domain.model.conversacion;

import java.util.UUID;

/** Identidad de un mensaje de Renasia (tabla `mensajes_renasia`). */
public record MensajeRenasiaId(UUID value) {

    public MensajeRenasiaId {
        if (value == null) {
            throw new IllegalArgumentException("MensajeRenasiaId no puede ser null");
        }
    }

    public static MensajeRenasiaId of(UUID value) {
        return new MensajeRenasiaId(value);
    }

    public static MensajeRenasiaId newId() {
        return new MensajeRenasiaId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
