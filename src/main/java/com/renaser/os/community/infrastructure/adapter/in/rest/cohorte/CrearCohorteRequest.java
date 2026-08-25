package com.renaser.os.community.infrastructure.adapter.in.rest.cohorte;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CrearCohorteRequest(@NotBlank String name, @NotNull LocalDate startDate, LocalDate endDate) {
}
