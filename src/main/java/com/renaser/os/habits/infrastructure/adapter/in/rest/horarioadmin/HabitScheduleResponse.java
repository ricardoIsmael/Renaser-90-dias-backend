package com.renaser.os.habits.infrastructure.adapter.in.rest.horarioadmin;

import com.renaser.os.habits.domain.model.horario.HorarioHabito;

import java.time.LocalTime;
import java.util.UUID;

/** Espejo de {@code HabitSchedule} (`habitsAdmin.ts`). */
public record HabitScheduleResponse(UUID id, int startDay, Integer endDay, DayTypeDto dayType,
                                     LocalTime defaultTriggerTime, LocalTime defaultLimitTime) {

    public static HabitScheduleResponse from(HorarioHabito horario) {
        return new HabitScheduleResponse(horario.id().value(), horario.diaInicio(), horario.diaFin(),
                DayTypeDto.from(horario.tipoDia()), horario.horaDisparo(), horario.horaLimite());
    }
}
