package com.renaser.os.chat.domain.model.conversacion;

import java.util.UUID;

/**
 * Identidad de una conversacion (tabla `conversaciones`). Valida y envuelve un UUID, pero <b>no lo genera</b>:
 * la generacion vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code ConversacionId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD §5.4.7: {@code domain/} sin aleatoriedad).
 */
public record ConversacionId(UUID value) {

    public ConversacionId {
        if (value == null) {
            throw new IllegalArgumentException("ConversacionId no puede ser null");
        }
    }

    public static ConversacionId of(UUID value) {
        return new ConversacionId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
