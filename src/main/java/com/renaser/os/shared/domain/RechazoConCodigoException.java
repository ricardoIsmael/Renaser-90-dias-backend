package com.renaser.os.shared.domain;

/**
 * Un pedido que se rechaza con un {@code codigo} de motivo que la app reconoce sin leer el texto (spec 055: la semana, los días
 * y las notas del mentor). {@code GlobalExceptionHandler} la responde con el formato de {@code ApiErrorConCodigo}
 * ({@code message}, {@code timestamp}, {@code codigo}): 400 si es {@link #SOLICITUD_INVALIDA}, 409 si es {@link #CONFLICTO} y 404 si es
 * {@link #NO_ENCONTRADO} (spec 056: «esa persona no sigue su programa» es un 404 con motivo, que la app distingue de «no existe»).
 *
 * <p>El 403 con motivo no usa esta clase: es {@link NotAuthorizedException} con su {@code codigo}.
 */
public class RechazoConCodigoException extends RuntimeException {

    /** 400: lo que se mandó no sirve (una fecha que no es viernes, una nota vacía). */
    public static final int SOLICITUD_INVALIDA = 400;
    /** 409: el pedido es válido, pero el estado de las cosas ya no lo permite (una nota de otro día). */
    public static final int CONFLICTO = 409;
    /** 404 con motivo: existe, pero no hay lo que se pide (una persona que no sigue su programa). Un 404 sin motivo sigue siendo {@code NoSuchElementException}. */
    public static final int NO_ENCONTRADO = 404;

    private final int estado;
    private final String codigo;

    public RechazoConCodigoException(int estado, String codigo, String mensaje) {
        super(mensaje);
        if (estado != SOLICITUD_INVALIDA && estado != CONFLICTO && estado != NO_ENCONTRADO) {
            throw new IllegalArgumentException("Solo 400, 404 o 409: " + estado);
        }
        this.estado = estado;
        this.codigo = codigo;
    }

    public static RechazoConCodigoException invalido(String codigo, String mensaje) {
        return new RechazoConCodigoException(SOLICITUD_INVALIDA, codigo, mensaje);
    }

    public static RechazoConCodigoException conflicto(String codigo, String mensaje) {
        return new RechazoConCodigoException(CONFLICTO, codigo, mensaje);
    }

    public static RechazoConCodigoException noEncontrado(String codigo, String mensaje) {
        return new RechazoConCodigoException(NO_ENCONTRADO, codigo, mensaje);
    }

    public int estado() {
        return estado;
    }

    public String codigo() {
        return codigo;
    }
}
