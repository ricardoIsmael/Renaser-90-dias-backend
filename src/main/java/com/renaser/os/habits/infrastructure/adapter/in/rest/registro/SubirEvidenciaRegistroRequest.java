package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/** {@code tipo}: FOTO/VIDEO/AUDIO/TEXTO/CAPTURA. Ver `RegistrarEvidenciaPort` para que campos exige cada uno. */
public record SubirEvidenciaRegistroRequest(@NotBlank String tipo, String bucket, String rutaStorage,
                                             String contenidoTexto, Instant timestampExif, Double gpsLat,
                                             Double gpsLng) {
}
