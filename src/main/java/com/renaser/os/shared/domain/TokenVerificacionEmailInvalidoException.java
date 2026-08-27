package com.renaser.os.shared.domain;

/**
 * El token de verificacion de email que acompaña al alta no existe, ya vencio, ya se uso, o no
 * corresponde al email del comando (alguien verifico un correo y trato de usar el token para
 * dar de alta uno distinto). Mensaje generico por el mismo motivo que
 * {@link TokenResetInvalidoException}.
 */
public class TokenVerificacionEmailInvalidoException extends RuntimeException {

    public TokenVerificacionEmailInvalidoException() {
        super("Verifica tu correo antes de enviar la solicitud");
    }
}
