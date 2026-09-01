package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/v1/auth/social/complete} (docs/MODULO_AUTH.md §6.10). <b>Nunca</b> lleva
 * correo ni ningun dato del proveedor: eso viaja solo dentro del {@code registroPendienteToken}
 * que devolvio {@code POST /auth/social} — es el blindaje central de este endpoint (si el
 * cliente pudiera mandar el correo aca, cualquiera completaria un registro con el correo de otra
 * persona).
 *
 * <p>{@code fullName} SI se puede corregir respecto de lo que devolvio el proveedor.
 * {@code phone}/{@code city} son opcionales (D-61), igual que en el alta por formulario.
 */
public record CompletarRegistroSocialRequest(
        @NotBlank String registroPendienteToken,
        @NotBlank String fullName,
        String phone,
        String city) {

    /** El token de continuacion es una credencial de un solo uso: nunca al log. */
    @Override
    public String toString() {
        return "CompletarRegistroSocialRequest[registroPendienteToken=oculto, fullName=" + fullName + "]";
    }
}
