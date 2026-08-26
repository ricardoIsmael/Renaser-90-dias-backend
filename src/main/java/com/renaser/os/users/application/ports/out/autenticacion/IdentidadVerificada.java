package com.renaser.os.users.application.ports.out.autenticacion;

/**
 * Resultado de {@link VerificadorIdentidadProveedor#verificar}. Deliberadamente pobre — nunca
 * el ID token entero ni ningun dato mas alla de lo que el caso de uso compositor necesita para
 * resolver {@code (proveedor, sujeto)} (docs/MODULO_AUTH.md §6.4).
 *
 * <p>{@code sujeto} es el {@code sub} del proveedor: estable, opaco, NUNCA el email — la
 * identidad se resuelve siempre por {@code (proveedor, sujeto)}, nunca por email (regla de
 * seguridad no negociable de §6.4).
 *
 * <p>{@code nombre} puede ser {@code null}: Apple solo lo manda una unica vez, en el primer
 * login del usuario, fuera del ID token (ver {@code AppleIdentidadAdapter}).
 */
public record IdentidadVerificada(String sujeto, String email, boolean emailVerificado, String nombre) {

    public IdentidadVerificada {
        if (sujeto == null || sujeto.isBlank()) {
            throw new IllegalArgumentException("sujeto es obligatorio: es la clave de identidad del proveedor");
        }
    }
}
