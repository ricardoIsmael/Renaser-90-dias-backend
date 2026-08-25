package com.renaser.os.rocks.infrastructure.adapter.in.rest.verdugo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/** {@code destinoTipo}: ROCA_DIARIA | REGISTRO_HABITO. {@code resultado}: COMPLETADO | POSTERGADO | POSPUESTO_30. */
public record RegistrarEventoVerdugoRequest(@NotBlank String destinoTipo, @NotNull UUID destinoId,
                                             @NotNull Instant disparadoEn, @NotBlank String resultado) {
}
