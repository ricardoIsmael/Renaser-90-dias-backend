package com.renaser.os.users.infrastructure.adapter.in.rest.participante;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * D-66: {@code startDate} es SIEMPRE obligatorio — a diferencia del `activate-program`
 * del backend viejo (que aceptaba omitirlo y calculaba "mañana"), la regla de negocio
 * vigente exige que el aprendiz elija de forma explicita entre las 4 fechas que devuelve
 * {@code GET /api/v1/onboarding/activate-program}. Formato {@code yyyy-MM-dd} (Jackson
 * lo deserializa nativo contra {@link LocalDate}).
 */
public record ActivarProgramaRequest(@NotNull LocalDate startDate) {
}
