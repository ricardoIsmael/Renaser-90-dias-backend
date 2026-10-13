package com.renaser.os.shared.domain;

/**
 * Un cambio crítico de la consola Soporte TI sin reautenticación válida (spec 002, FR-008; contrato §2). Responde
 * <b>428</b> con {@code codigo: "REAUTENTICACION_REQUERIDA"}; la app pide la contraseña y reintenta.
 */
public final class ReautenticacionRequeridaException extends RuntimeException {

    public ReautenticacionRequeridaException() {
        super("Este cambio es crítico: confirma tu contraseña para hacerlo.");
    }
}
