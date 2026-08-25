package com.renaser.os.users.infrastructure.adapter.in.rest.participante;

/** Espejo literal de {@code {deactivated: boolean}} del backend viejo — DELETE es
 * idempotente, nunca es un error que ya estuviera desactivado. */
public record DeactivateSelfTrackingResponse(boolean deactivated) {
}
