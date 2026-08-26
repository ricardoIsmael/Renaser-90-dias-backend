package com.renaser.os.habits.infrastructure.adapter.in.rest.racha;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/**
 * El cierre de la racha va SIEMPRE con evidencia (repo viejo: {@code PhoneFreeCompleteInput}).
 * {@code tipo}: FOTO/AUDIO/TEXTO. Ver {@code CerrarRachaUseCase} para que campos exige cada uno.
 */
public record CompletarRachaRequest(@NotBlank String tipo, String bucket, String rutaStorage, String contenidoTexto,
                                     Instant timestampExif) {
}
