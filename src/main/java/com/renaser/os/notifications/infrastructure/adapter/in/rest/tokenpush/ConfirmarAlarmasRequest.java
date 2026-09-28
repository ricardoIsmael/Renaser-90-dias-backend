package com.renaser.os.notifications.infrastructure.adapter.in.rest.tokenpush;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/v1/push-tokens/alarmas-locales} (D-217): el token de ESTE dispositivo. */
public record ConfirmarAlarmasRequest(@NotBlank @Size(max = 2000) String token) {
}
