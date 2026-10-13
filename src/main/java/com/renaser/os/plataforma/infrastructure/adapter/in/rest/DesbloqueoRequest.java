package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.renaser.os.plataforma.application.ports.in.DesbloquearConsolaUseCase.Para;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code POST /soporte-ti/desbloqueo} (contrato §6). */
record DesbloqueoRequest(@NotBlank @Size(max = 200) String contrasena, @NotNull Para para) {
}
