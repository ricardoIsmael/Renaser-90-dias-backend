package com.renaser.os.users.infrastructure.adapter.in.rest.user;

import jakarta.validation.constraints.NotBlank;

public record ConfirmarAvatarRequest(@NotBlank String bucket, @NotBlank String ruta) {
}
