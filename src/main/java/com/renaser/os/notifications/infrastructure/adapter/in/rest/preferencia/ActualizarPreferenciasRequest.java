package com.renaser.os.notifications.infrastructure.adapter.in.rest.preferencia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Espejo de {@code profile/schema.ts:PatchNotificationPreferencesInput}. */
public record ActualizarPreferenciasRequest(@NotEmpty List<@Valid Item> preferences) {

    public record Item(@NotNull String type, boolean enabled) {
    }
}
