package com.renaser.os.users.domain.model.accountrequest;

import java.util.UUID;

/**
 * Identidad de una solicitud de alta (tabla `solicitudes_cuenta`).
 *
 * Distinta de UserId: una AccountRequest tiene su propio id, y ademas guarda el
 * UserId de Supabase Auth que tendra el usuario si se aprueba (ver AccountRequest).
 *
 * <p>Valida y envuelve un UUID, pero <b>no lo genera</b>: la generacion vive fuera de
 * {@code domain/}, detras del puerto {@link com.renaser.os.shared.domain.IdGenerator}, y el
 * caso de uso arma el id con {@code AccountRequestId.of(idGenerator.newId())} antes de invocar
 * la factoria del agregado (CLAUDE.MD §5.4.7: {@code domain/} sin aleatoriedad).
 */
public record AccountRequestId(UUID value) {

    public AccountRequestId {
        if (value == null) {
            throw new IllegalArgumentException("AccountRequestId no puede ser null");
        }
    }

    public static AccountRequestId of(UUID value) {
        return new AccountRequestId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
