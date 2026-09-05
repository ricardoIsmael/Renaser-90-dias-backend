package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerificarCodigoResetContrasenaRequest(@NotBlank @Email String email,
                                                     @NotBlank @Pattern(regexp = "\\d{6}") String codigo) {

    /** El codigo es una credencial de un solo uso: no se loguea. */
    @Override
    public String toString() {
        return "VerificarCodigoResetContrasenaRequest[email=" + email + ", codigo=oculto]";
    }
}
