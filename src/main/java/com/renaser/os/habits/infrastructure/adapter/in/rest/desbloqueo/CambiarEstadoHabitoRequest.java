package com.renaser.os.habits.infrastructure.adapter.in.rest.desbloqueo;

import jakarta.validation.constraints.NotNull;

/** {@code active: false} pausa el habito para este aprendiz; {@code true} lo reactiva (D-87). */
public record CambiarEstadoHabitoRequest(@NotNull Boolean active) {
}
