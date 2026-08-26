package com.renaser.os.habits.infrastructure.adapter.in.rest.eleccion;

import com.renaser.os.habits.domain.model.eleccion.EleccionDiaSemanal;

import java.time.LocalDate;
import java.util.UUID;

public record WeeklyHabitDayResponse(UUID habitId, LocalDate date, LocalDate weekStart) {

    public static WeeklyHabitDayResponse from(EleccionDiaSemanal e) {
        return new WeeklyHabitDayResponse(e.habitoId().value(), e.fechaEjecucion(), e.semanaInicio());
    }
}
