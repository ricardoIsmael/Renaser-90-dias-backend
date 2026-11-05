package com.renaser.os.calendar.application.ports.out.meet;

/**
 * Google no pudo atender ahora (red, 5xx, 429, cuota o el límite propio de pedidos por minuto): se reintenta más tarde (spec 040, FR-008).
 * {@code codigoHttp} es 0 si ni siquiera hubo respuesta.
 */
public final class GoogleNoRespondeException extends RuntimeException {

    private final int codigoHttp;

    public GoogleNoRespondeException(String motivo, int codigoHttp) {
        super(motivo);
        this.codigoHttp = codigoHttp;
    }

    public int codigoHttp() {
        return codigoHttp;
    }
}
