package com.renaser.os.notifications.domain.model.tokenpush;

import java.util.UUID;

/**
 * Identidad del agregado {@link TokenPush}. Valida y envuelve un UUID, pero <b>no lo genera</b>:
 * la generacion vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code TokenPushId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD §5.4.7: {@code domain/} sin aleatoriedad).
 */
public record TokenPushId(UUID value) {

    public TokenPushId {
        if (value == null) {
            throw new IllegalArgumentException("TokenPushId no puede ser null");
        }
    }

    public static TokenPushId of(UUID value) {
        return new TokenPushId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
