package com.renaser.os.shared.domain;

/**
 * La consola Soporte TI está bloqueada: falta el token de desbloqueo o venció por inactividad (spec 002, FR-024;
 * contrato §2). Responde <b>423</b> con {@code codigo: "CONSOLA_BLOQUEADA"}; la app pide la contraseña de nuevo.
 */
public final class ConsolaBloqueadaException extends RuntimeException {

    public ConsolaBloqueadaException() {
        super("La consola está bloqueada. Escribe tu contraseña para seguir.");
    }
}
