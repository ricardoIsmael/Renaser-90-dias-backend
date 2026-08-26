package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank @Email String email, @NotBlank String contrasena) {

    /** Nunca en un log: es la contrasena en claro tal como la mando el cliente. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", contrasena=oculta]";
    }
}
