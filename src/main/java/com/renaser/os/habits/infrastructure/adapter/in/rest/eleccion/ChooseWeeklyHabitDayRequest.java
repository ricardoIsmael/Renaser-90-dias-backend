package com.renaser.os.habits.infrastructure.adapter.in.rest.eleccion;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ChooseWeeklyHabitDayRequest(@NotNull LocalDate date) {
}
