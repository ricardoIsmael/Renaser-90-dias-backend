package com.renaser.os.users.application.ports.out.autenticacion;

import com.renaser.os.shared.domain.IdentidadProveedorInvalidaException;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;

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

    /**
     * Un {@code email_verified=false} significa que el proveedor no responde por ese correo, y
     * todo el flujo social se apoya en que el email llega pre-verificado (docs/MODULO_AUTH.md
     * §6.4) — si el proveedor no lo confirma, esa premisa no se cumple y no se sigue.
     *
     * <p>Vive aca, y no en un caso de uso, porque la exigencia es de <b>la identidad misma</b> y
     * la comparten los dos flujos que la consumen: el login social
     * ({@code AutenticacionSocialService}) y la vinculacion a una cuenta ya existente
     * ({@code VinculacionIdentidadSocialService}). Duplicar el chequeo seria dejar abierta la
     * posibilidad de que un flujo nuevo se olvide de hacerlo.
     */
    public void exigirEmailVerificado(ProveedorIdentidad proveedor) {
        if (!emailVerificado) {
            throw new IdentidadProveedorInvalidaException(proveedor.name());
        }
    }
}
