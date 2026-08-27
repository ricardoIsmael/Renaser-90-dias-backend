package com.renaser.os.users.infrastructure.adapter.in.rest.user;

import jakarta.validation.constraints.NotBlank;

public record SolicitarUrlAvatarRequest(@NotBlank String tipoContenido) {
}
