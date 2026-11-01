package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.habits.domain.model.preferencia.PlanDeLaSemana.DiaDeLaFecha;
import com.renaser.os.habits.domain.model.preferencia.SemanaCalendaria;

import java.time.LocalDate;
import java.util.List;

/** {@code GET /api/v1/habit-preferences/{habitId}/week}: los siete dias de una semana de un habito (spec 028, D-296). */
public record WeekResponse(LocalDate weekStart, LocalDate weekEnd, List<HabitDateResponse> days) {

    static WeekResponse from(SemanaCalendaria semana, List<DiaDeLaFecha> dias) {
        return new WeekResponse(semana.lunes(), semana.domingo(), dias.stream().map(HabitDateResponse::from).toList());
    }
}
