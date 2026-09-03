package com.renaser.os.chat.domain.model.mensaje;

import java.util.UUID;

/**
 * Identidad de un mensaje (tabla `mensajes`). Valida y envuelve un UUID, pero <b>no lo genera</b>:
 * la generacion vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code MensajeId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD §5.4.7: {@code domain/} sin aleatoriedad).
 */
public record MensajeId(UUID value) {

    public MensajeId {
        if (value == null) {
            throw new IllegalArgumentException("MensajeId no puede ser null");
        }
    }

    public static MensajeId of(UUID value) {
        return new MensajeId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
