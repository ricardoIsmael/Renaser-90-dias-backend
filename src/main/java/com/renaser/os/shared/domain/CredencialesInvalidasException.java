package com.renaser.os.shared.domain;

/**
 * Email o contrasena incorrectos. Un mensaje deliberadamente vago, sea cual sea la causa real
 * (el email no existe, la cuenta no tiene contrasena, esta suspendida, o la contrasena no
 * coincide) — distinguir esos casos en la respuesta permite a un atacante enumerar que emails
 * estan registrados.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Email o contrasena incorrectos");
    }
}
