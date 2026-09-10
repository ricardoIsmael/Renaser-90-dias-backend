package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/** {@code periodStart}/{@code periodEnd} (V48): las dos o ninguna — "septiembre, del 1 al 30". El
 * ultimo dia entra entero. Sin ellas, el grupo no caduca. */
public record CrearCelulaRequest(@NotBlank String name, @NotNull UUID cohortId, String videoCallUrl,
                                  LocalDate periodStart, LocalDate periodEnd) {
}
