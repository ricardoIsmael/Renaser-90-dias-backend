package com.renaser.os.community.infrastructure.adapter.in.rest.cohorte;

import jakarta.validation.constraints.NotBlank;

/** {@code status} en ingles (PLANNED/ACTIVE/COMPLETED), traducido en el controller. */
public record CambiarEstadoCohorteRequest(@NotBlank String status) {
}
