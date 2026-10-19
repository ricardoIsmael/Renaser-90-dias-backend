package com.renaser.os.users.api;

/**
 * Un cambio de un rol personalizado que choca con su estado actual (D-272): <b>409</b> con un {@code codigo} para que la
 * app sepa qué pasó sin leer el texto (contrato de la fase 2, §4, aditivo del 2026-10-14).
 * <ul>
 *   <li>{@value #VERSION_CAMBIADA}: otra persona lo cambió después de que se cargó (bloqueo optimista);</li>
 *   <li>{@value #ROL_CON_PERSONAS}: tiene personas asignadas y por eso no se borra ni cambia de rol base (FR-033).</li>
 * </ul>
 * Extiende {@link IllegalStateException}: si la recibe un camino que no conoce el código, sigue siendo un 409.
 */
public final class ConflictoDeRolPersonalizadoException extends IllegalStateException {

    public static final String VERSION_CAMBIADA = "VERSION_CAMBIADA";
    public static final String ROL_CON_PERSONAS = "ROL_CON_PERSONAS";

    private final String codigo;

    private ConflictoDeRolPersonalizadoException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public static ConflictoDeRolPersonalizadoException versionCambiada() {
        return new ConflictoDeRolPersonalizadoException(VERSION_CAMBIADA,
                "Otra persona cambió este rol recién. Vuelve a cargarlo.");
    }

    public static ConflictoDeRolPersonalizadoException conPersonas(String mensaje) {
        return new ConflictoDeRolPersonalizadoException(ROL_CON_PERSONAS, mensaje);
    }

    public String codigo() {
        return codigo;
    }
}
