package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocamaestra;

import java.time.Instant;

/**
 * Cuerpo del 409 de {@code PUT /api/v1/rocks/master/{eje}} cuando se intenta cambiar una Roca
 * Maestra ya definida (D-234). Lleva {@code message} igual que {@code ApiErrorResponse} —la app lo
 * muestra tal cual— mas un {@code codigo} estable para distinguirlo sin leer el texto.
 */
public record RocaMaestraFijaResponse(String codigo, String message, Instant timestamp) {
}
