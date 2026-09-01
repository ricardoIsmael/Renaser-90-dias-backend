package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code POST /api/v1/auth/social} (docs/MODULO_AUTH.md §6.1). Desde D-65 (2026-09-01, §6.10)
 * ya NO lleva {@code phone}/{@code city}: este paso solo verifica la identidad contra el
 * proveedor. Si la identidad es nueva, esos datos se piden despues, en
 * {@code POST /auth/social/complete}, junto con el resto del formulario de confirmacion.
 */
public record LoginSocialRequest(
        @NotNull ProveedorIdentidad proveedor,
        @NotBlank String code,
        @NotBlank String codeVerifier,
        @NotBlank String redirectUri) {

    /** El `code` y el `code_verifier` son credenciales de un solo uso: nunca al log. */
    @Override
    public String toString() {
        return "LoginSocialRequest[proveedor=" + proveedor + ", code=oculto, codeVerifier=oculto, redirectUri="
                + redirectUri + "]";
    }
}
