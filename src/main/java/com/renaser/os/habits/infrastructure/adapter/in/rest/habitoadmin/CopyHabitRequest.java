package com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin;

import jakarta.validation.constraints.NotBlank;

/** «Copiar a otra formacion» (spec 023): la formacion destino y la categoria de ESA formacion donde queda la copia. */
public record CopyHabitRequest(@NotBlank String formation, @NotBlank String category) {
}
