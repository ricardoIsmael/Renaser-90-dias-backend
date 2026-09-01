package com.renaser.os.shared.domain;

/**
 * El token de continuacion del alta social ({@code POST /auth/social/complete},
 * docs/MODULO_AUTH.md §6.10) no existe, ya vencio, o ya se uso. Mensaje deliberadamente generico
 * y sin distinguir los tres casos, mismo criterio que {@code TokenResetInvalidoException}.
 *
 * <p>No hay "reintentar" este paso: el `code` de OAuth que dio origen a la identidad verificada
 * ya se gasto en el primer toque de {@code POST /auth/social}, asi que la unica salida es
 * rehacer el flujo del proveedor desde el principio.
 */
public class RegistroPendienteSocialInvalidoException extends RuntimeException {

    public RegistroPendienteSocialInvalidoException() {
        super("El registro pendiente no es valido, ya vencio o ya se uso: volve a iniciar sesion con tu "
                + "proveedor social para continuar");
    }
}
