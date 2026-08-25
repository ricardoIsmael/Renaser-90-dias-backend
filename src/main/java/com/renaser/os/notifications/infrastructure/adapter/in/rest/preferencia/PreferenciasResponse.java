package com.renaser.os.notifications.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.notifications.domain.model.preferencia.PreferenciaNotificacion;

import java.util.List;

/** Espejo de {@code profile/schema.ts:NotificationPreferencesResponse} ({@code {preferences: [...]}}). */
public record PreferenciasResponse(List<PreferenciaItemDto> preferences) {

    public static PreferenciasResponse from(List<PreferenciaNotificacion> preferencias) {
        return new PreferenciasResponse(preferencias.stream().map(PreferenciaItemDto::from).toList());
    }
}
