package com.renaser.os.shared.domain;

/**
 * El codigo de verificacion de email no coincide, ya vencio, o se agotaron los intentos.
 * Mensaje deliberadamente generico, mismo criterio que {@link TokenResetInvalidoException}:
 * distinguir el motivo exacto ante el cliente no le sirve a nadie mas que a quien esta
 * tanteando codigos.
 */
public class CodigoVerificacionInvalidoException extends RuntimeException {

    public CodigoVerificacionInvalidoException() {
        super("El codigo no es valido o ya vencio");
    }
}
