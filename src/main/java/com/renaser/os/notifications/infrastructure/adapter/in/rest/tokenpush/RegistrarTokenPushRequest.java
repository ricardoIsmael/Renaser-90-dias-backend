package com.renaser.os.notifications.infrastructure.adapter.in.rest.tokenpush;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Espejo de {@code chat/schema.ts:RegisterPushTokenInput}. Ruptura de contrato conocida y
 * heredada (mismo criterio que `docs/MODULO_PHASECONTRACTS.md` §4): {@code platform} ahora
 * espera el literal Postgres en MAYUSCULAS ({@code "IOS"}/{@code "ANDROID"}/{@code "WEB"}), el repo viejo
 * aceptaba minusculas ({@code "ios"}/{@code "android"}). Coordinar con la app antes de liberar.
 */
public record RegistrarTokenPushRequest(@NotBlank @Size(max = 2000) String token, String platform) {
}
