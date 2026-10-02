package com.renaser.os.users.infrastructure.adapter.in.rest.eliminacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmarEliminacionRequest(@NotBlank @Size(max = 254) String email,
                                          @NotBlank @Size(max = 12) String codigo) {
}
