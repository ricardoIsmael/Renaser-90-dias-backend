package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocadiaria;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/** {@code tipo}: FOTO/VIDEO/AUDIO/TEXTO/CAPTURA. Ver `CompletarRocaDiariaUseCase` para qué campos exige cada uno. */
public record CompletarRocaDiariaRequest(@NotBlank String tipo, String bucket, String rutaStorage, String contenidoTexto,
                                          Instant timestampExif, Double gpsLat, Double gpsLng) {
}
