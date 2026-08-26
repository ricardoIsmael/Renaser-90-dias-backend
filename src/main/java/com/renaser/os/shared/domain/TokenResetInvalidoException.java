package com.renaser.os.shared.domain;

/**
 * El token de reseteo de contrasena no existe, ya vencio, o ya se uso. Mensaje deliberadamente
 * generico: los tres casos colapsan en {@code Optional.empty()} del lado del puerto (docs/
 * MODULO_AUTH.md §2.2) y no hay razon de negocio para distinguirlos ante el cliente — distinguir
 * "vencido" de "ya usado" no le sirve a nadie mas que a un atacante tanteando tokens.
 */
public class TokenResetInvalidoException extends RuntimeException {

    public TokenResetInvalidoException() {
        super("El enlace de reseteo de contrasena no es valido o ya vencio");
    }
}
