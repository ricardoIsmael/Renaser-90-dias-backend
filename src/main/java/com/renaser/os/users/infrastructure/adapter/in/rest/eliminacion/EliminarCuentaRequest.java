package com.renaser.os.users.infrastructure.adapter.in.rest.eliminacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code confirmEmail}: el correo de la cuenta, escrito a mano como confirmacion fuerte. */
public record EliminarCuentaRequest(@NotBlank @Size(max = 254) String confirmEmail) {
}
