package com.renaser.os.onboarding.infrastructure.adapter.in.rest.estado;

/** Todos los campos opcionales: el cliente manda solo lo que cambio (ver EstadoOnboarding.avanzar). */
public record AvanzarEstadoRequest(String flow, String section, Integer step, String flowProgress) {
}
