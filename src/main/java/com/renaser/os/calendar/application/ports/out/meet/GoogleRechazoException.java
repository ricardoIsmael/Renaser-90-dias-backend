package com.renaser.os.calendar.application.ports.out.meet;

/** Google respondió que no (400, 403 por falta de permiso, 404…): reintentar lo mismo no cambia nada. El motivo es un texto corto, sin el cuerpo de Google. */
public final class GoogleRechazoException extends RuntimeException {

    private final int codigoHttp;

    public GoogleRechazoException(String motivo, int codigoHttp) {
        super(motivo);
        this.codigoHttp = codigoHttp;
    }

    public int codigoHttp() {
        return codigoHttp;
    }
}
