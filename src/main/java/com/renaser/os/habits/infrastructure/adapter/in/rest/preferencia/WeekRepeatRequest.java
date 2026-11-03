package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Cuerpo de {@code POST /api/v1/habit-preferences/week-repeat} (spec 028, D-296). {@code replace} obligatorio: omitirlo no puede significar
 * «reemplaza lo que ya cambie».
 */
public record WeekRepeatRequest(@NotNull LocalDate sourceWeekStart, @NotNull LocalDate targetWeekStart, @NotNull Boolean replace) {
}
