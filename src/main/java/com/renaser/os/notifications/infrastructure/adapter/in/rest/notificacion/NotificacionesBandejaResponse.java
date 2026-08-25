package com.renaser.os.notifications.infrastructure.adapter.in.rest.notificacion;

import com.renaser.os.notifications.domain.model.notificacion.Notificacion;

import java.util.List;

/** Espejo de {@code notifications/schema.ts:NotificationsListResponse} ({@code {items: [...]}}). */
public record NotificacionesBandejaResponse(List<NotificacionResponse> items) {

    public static NotificacionesBandejaResponse from(List<Notificacion> notificaciones) {
        return new NotificacionesBandejaResponse(notificaciones.stream().map(NotificacionResponse::from).toList());
    }
}
