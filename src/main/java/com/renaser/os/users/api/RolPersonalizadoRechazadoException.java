package com.renaser.os.users.api;

/**
 * Un rol personalizado que no se puede guardar así (D-269): <b>400</b>, como dice el contrato de la fase 2 (§4.5), con un
 * {@code codigo} para que la app sepa qué pasó sin leer el texto (aditivo del 2026-10-14).
 * <ul>
 *   <li>{@value #NOMBRE_REPETIDO}: el nombre es el de otro rol personalizado o el de un rol base (sin mirar mayúsculas
 *       ni tildes);</li>
 *   <li>{@value #MAXIMO_DE_ROLES}: ya hay 20 roles personalizados activos (FR-034).</li>
 * </ul>
 * Extiende {@link IllegalArgumentException}: si la recibe un camino que no conoce el código, sigue siendo un 400.
 */
public final class RolPersonalizadoRechazadoException extends IllegalArgumentException {

    public static final String NOMBRE_REPETIDO = "NOMBRE_REPETIDO";
    public static final String MAXIMO_DE_ROLES = "MAXIMO_DE_ROLES";

    private final String codigo;

    private RolPersonalizadoRechazadoException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public static RolPersonalizadoRechazadoException nombreRepetido(String mensaje) {
        return new RolPersonalizadoRechazadoException(NOMBRE_REPETIDO, mensaje);
    }

    public static RolPersonalizadoRechazadoException maximoDeRoles(String mensaje) {
        return new RolPersonalizadoRechazadoException(MAXIMO_DE_ROLES, mensaje);
    }

    public String codigo() {
        return codigo;
    }
}
