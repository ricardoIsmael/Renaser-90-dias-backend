package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmarCodigoVerificacionEmailRequest(@NotBlank @Email String email,
                                                        @NotBlank @Pattern(regexp = "\\d{6}") String codigo) {
}
