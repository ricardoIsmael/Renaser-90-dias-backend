package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SolicitarResetContrasenaRequest(@NotBlank @Email String email) {
}
