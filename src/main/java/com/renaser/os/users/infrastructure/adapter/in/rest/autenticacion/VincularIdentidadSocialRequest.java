package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code POST /api/v1/auth/social/link} (docs/MODULO_AUTH.md §6.9). Mismos campos que
 * {@link LoginSocialRequest} <b>menos</b> {@code phone}/{@code city}: aca la cuenta ya existe,
 * no hay alta que completar.
 *
 * <p>No lleva —ni puede llevar— ningun campo que identifique al usuario: quien vincula sale de
 * la sesion, nunca del cuerpo. Mismo blindaje por compilador que el {@code role} ausente del
 * alta publica (CLAUDE.MD §5.3.3).
 */
public record VincularIdentidadSocialRequest(
        @NotNull ProveedorIdentidad proveedor,
        @NotBlank String code,
        @NotBlank String codeVerifier,
        @NotBlank String redirectUri) {

    /** El `code` y el `code_verifier` son credenciales de un solo uso: nunca al log. */
    @Override
    public String toString() {
        return "VincularIdentidadSocialRequest[proveedor=" + proveedor + ", code=oculto, codeVerifier=oculto, "
                + "redirectUri=" + redirectUri + "]";
    }
}
