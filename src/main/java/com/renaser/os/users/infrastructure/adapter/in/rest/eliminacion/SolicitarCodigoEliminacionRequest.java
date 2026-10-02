package com.renaser.os.users.infrastructure.adapter.in.rest.eliminacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SolicitarCodigoEliminacionRequest(@NotBlank @Size(max = 254) String email) {
}
