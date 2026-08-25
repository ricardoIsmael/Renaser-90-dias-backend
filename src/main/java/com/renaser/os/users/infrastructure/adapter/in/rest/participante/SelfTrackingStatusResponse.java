package com.renaser.os.users.infrastructure.adapter.in.rest.participante;

/**
 * Espejo literal de {@code SelfTrackingStatusResponse} del backend viejo
 * (src/features/mentor/service.ts) — el mismo shape que ya consume
 * `getSelfTrackingStatus` en `mentorService.ts` de la app movil.
 */
public record SelfTrackingStatusResponse(boolean active) {
}
