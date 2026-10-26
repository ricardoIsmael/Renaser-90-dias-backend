package com.renaser.os.shared.domain;

/**
 * La funcionalidad existe y está prendida, pero a esta persona no le corresponde (spec 002, fase 2): está en
 * despliegue progresivo y todavía no le tocó, o su rol personalizado no la incluye. {@code GlobalExceptionHandler} la
 * traduce a <b>404</b> con {@code codigo: "FUNCIONALIDAD_NO_DISPONIBLE"}: la app la esconde en vez de mostrar el aviso
 * de mantenimiento (decisión del dueño, P1 del 2026-10-14).
 *
 * <p>Extiende {@link FuncionalidadApagadaException} a propósito: el chat, SER y los hábitos ya atrapan esa excepción
 * para cerrar un WebSocket o terminar un stream, y tienen que hacer lo mismo acá. El HTTP la distingue porque Spring
 * elige el manejador de la excepción más específica.
 */
public final class FuncionalidadNoDisponibleException extends FuncionalidadApagadaException {

    public static final String MENSAJE = "Esta función todavía no está disponible para tu cuenta.";

    public FuncionalidadNoDisponibleException(String clave) {
        super(clave, MENSAJE);
    }
}
