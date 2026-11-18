package com.renaser.os.shared.domain;

/**
 * El actor no puede hacer lo que pidio: 403. Puede llevar un {@code codigo} de motivo (nulo si no lo hay) para que la app
 * lo reconozca sin leer el texto: viaja en el cuerpo del 403 con el formato de {@code ApiErrorConCodigo}
 * ({@code message}, {@code timestamp}, {@code codigo}), el mismo de la spec 002. Los motivos de hoy:
 * {@link #PROGRAMA_NO_ACTIVADO}, {@link #CUENTA_SUSPENDIDA}, {@link #SIN_PERMISO} y {@link #FUERA_DE_SU_ALCANCE}.
 */
public class NotAuthorizedException extends RuntimeException {

    /** El personal que todavia no eligio su Dia 1 (FR-019 de la spec 012): la app lo lleva a elegirlo. */
    public static final String PROGRAMA_NO_ACTIVADO = "PROGRAMA_NO_ACTIVADO";
    /** La cuenta esta suspendida. */
    public static final String CUENTA_SUSPENDIDA = "CUENTA_SUSPENDIDA";
    /** El rol no tiene el permiso que pide el endpoint. */
    public static final String SIN_PERMISO = "SIN_PERMISO";
    /** El lider de mentores pidio algo de una formacion que no es de las suyas (spec 056, FR-010): el grupo o la persona existen, pero no le tocan. */
    public static final String FUERA_DE_SU_ALCANCE = "FUERA_DE_SU_ALCANCE";

    private final String codigo;

    public NotAuthorizedException(String message) {
        this(message, null);
    }

    public NotAuthorizedException(String message, String codigo) {
        super(message);
        this.codigo = codigo;
    }

    /** El motivo en una palabra para la app, o {@code null} si este 403 no trae uno. */
    public String codigo() {
        return codigo;
    }
}
