package com.renaser.os.users.infrastructure.adapter.in.rest.eliminacion;

import jakarta.validation.constraints.Size;

/** Con uno alcanza: {@code contrasena} si la cuenta tiene, si no {@code codigo} (6 digitos del correo). */
public record CerrarMiCuentaRequest(@Size(max = 200) String contrasena, @Size(max = 12) String codigo) {
}
