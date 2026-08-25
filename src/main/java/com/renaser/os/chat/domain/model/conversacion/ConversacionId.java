package com.renaser.os.chat.domain.model.conversacion;

import java.util.UUID;

/** Identidad de una conversacion (tabla `conversaciones`). */
public record ConversacionId(UUID value) {

    public ConversacionId {
        if (value == null) {
            throw new IllegalArgumentException("ConversacionId no puede ser null");
        }
    }

    public static ConversacionId of(UUID value) {
        return new ConversacionId(value);
    }

    public static ConversacionId newId() {
        return new ConversacionId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
