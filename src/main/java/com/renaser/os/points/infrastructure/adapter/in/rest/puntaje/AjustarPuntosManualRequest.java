package com.renaser.os.points.infrastructure.adapter.in.rest.puntaje;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Sin campo `motivo`: el ajuste manual siempre es MANUAL_ADJUSTMENT, forzado server-side. */
public record AjustarPuntosManualRequest(@NotBlank String participanteId, @NotNull Integer delta,
                                          String nota) {
}
