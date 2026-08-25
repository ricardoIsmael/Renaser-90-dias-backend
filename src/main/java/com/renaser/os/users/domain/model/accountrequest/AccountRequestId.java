package com.renaser.os.users.domain.model.accountrequest;

import java.util.UUID;

/**
 * Identidad de una solicitud de alta (tabla `solicitudes_cuenta`).
 *
 * Distinta de UserId: una AccountRequest tiene su propio id, y ademas guarda el
 * UserId de Supabase Auth que tendra el usuario si se aprueba (ver AccountRequest).
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

    public static AccountRequestId newId() {
        return new AccountRequestId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
