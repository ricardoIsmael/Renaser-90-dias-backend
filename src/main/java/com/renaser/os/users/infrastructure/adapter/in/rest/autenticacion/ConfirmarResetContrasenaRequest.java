package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmarResetContrasenaRequest(@NotBlank String token,
                                               @NotBlank @Size(min = 12, max = 200) String contrasenaNueva) {

    /** Ni el token ni la contrasena nueva se loguean nunca: son credenciales en claro tal como las mando el cliente. */
    @Override
    public String toString() {
        return "ConfirmarResetContrasenaRequest[token=oculto, contrasenaNueva=oculta]";
    }
}
