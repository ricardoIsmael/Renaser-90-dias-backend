package com.renaser.os.notifications.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.notifications.domain.model.preferencia.PreferenciaNotificacion;

/** Espejo de {@code profile/schema.ts:NotificationPreferenceItem} ({@code {type, enabled}}). */
public record PreferenciaItemDto(String type, boolean enabled) {

    public static PreferenciaItemDto from(PreferenciaNotificacion p) {
        return new PreferenciaItemDto(p.tipo().name(), p.habilitada());
    }
}
