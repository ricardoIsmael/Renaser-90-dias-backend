package com.renaser.os.calendar.application.ports.out.meet;

/** No hay cuenta de Google conectada, o está revocada (spec 040, FR-009): hay que volver a conectarla. Es un 409 para quien la ve en un endpoint. */
public final class CuentaGoogleNoConectadaException extends IllegalStateException {

    public static final String MENSAJE = "Todavía no hay una cuenta de Google conectada.";

    public CuentaGoogleNoConectadaException() {
        super(MENSAJE);
    }
}
