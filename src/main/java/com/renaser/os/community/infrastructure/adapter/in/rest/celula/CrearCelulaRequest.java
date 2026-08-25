package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CrearCelulaRequest(@NotBlank String name, @NotNull UUID cohortId, String videoCallUrl) {
}
