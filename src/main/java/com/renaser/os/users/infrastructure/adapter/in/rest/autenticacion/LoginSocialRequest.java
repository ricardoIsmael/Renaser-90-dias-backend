package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code POST /api/v1/auth/social} (docs/MODULO_AUTH.md §6.1). {@code phone}/{@code city} son
 * opcionales: solo hacen falta si la identidad es nueva y el backend abre una AccountRequest — ver
 * la nota de diseño en {@code IniciarSesionConProveedorUseCase}.
 */
public record LoginSocialRequest(
        @NotNull ProveedorIdentidad proveedor,
        @NotBlank String code,
        @NotBlank String codeVerifier,
        @NotBlank String redirectUri,
        String phone,
        String city) {

    /** El `code` y el `code_verifier` son credenciales de un solo uso: nunca al log. */
    @Override
    public String toString() {
        return "LoginSocialRequest[proveedor=" + proveedor + ", code=oculto, codeVerifier=oculto, redirectUri="
                + redirectUri + "]";
    }
}
