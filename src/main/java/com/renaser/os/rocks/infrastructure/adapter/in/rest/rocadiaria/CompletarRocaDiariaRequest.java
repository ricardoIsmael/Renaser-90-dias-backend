package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocadiaria;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/**
 * {@code tipo}: FOTO/VIDEO/AUDIO/TEXTO/CAPTURA. Ver `CompletarRocaDiariaUseCase` para qué campos exige cada uno.
 *
 * <p>{@code esPrincipal} (Hueco #17): {@code null} = {@code true} — antes de esto el
 * campo no existía y el servicio lo mandaba siempre en {@code true}; un cliente viejo
 * que no lo manda sigue viendo el mismo comportamiento (mismo criterio que
 * {@code categoriaClave} nulo en `community`, CLAUDE.MD: no romper compatibilidad).
 */
public record CompletarRocaDiariaRequest(@NotBlank String tipo, String bucket, String rutaStorage, String contenidoTexto,
                                          Instant timestampExif, Double gpsLat, Double gpsLng, Boolean esPrincipal) {
}
