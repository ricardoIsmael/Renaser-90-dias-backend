package com.renaser.os.habits.infrastructure.adapter.in.rest.racha;

import jakarta.validation.constraints.NotNull;

public record IniciarRachaRequest(@NotNull Integer horasObjetivo) {
}
