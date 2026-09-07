package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.habits.application.ports.in.preferencia.EditarHorarioSemanalUseCase.DiaDeLaSemana;

import java.time.LocalTime;
import java.util.List;

/**
 * Los SIETE dias ya resueltos de {@code GET /api/v1/habit-preferences/{habitId}/weekdays}.
 *
 * <p>Vienen los siete y en orden de la semana aunque solo uno tenga hora propia: asi la pantalla
 * pinta la fila de dias leyendo una lista, sin mezclar "lo propio" con "lo general" — esa mezcla es
 * la precedencia, y vive en el servidor para que no existan dos implementaciones de la misma regla.
 *
 * <p>{@code active} es {@code false} cuando el aprendiz apago ese dia de la semana (V40). Viene con
 * la hora igual: el dia esta apagado, no sin horario, y encenderlo lo devuelve a esa hora.
 *
 * <p>{@code weekday} como nombre de {@link java.time.DayOfWeek} ({@code "MONDAY"}..{@code "SUNDAY"}),
 * igual que {@code activeWeekdays} en {@code GET /api/v1/habits}: mismo vocabulario en todo el cable.
 */
public record WeekdayScheduleResponse(List<WeekdayScheduleItemResponse> weekdays) {

    public static WeekdayScheduleResponse from(List<DiaDeLaSemana> dias) {
        return new WeekdayScheduleResponse(dias.stream().map(WeekdayScheduleItemResponse::from).toList());
    }

    /** {@code custom}: {@code true} si ese dia tiene hora propia; {@code false} si hereda la general. */
    public record WeekdayScheduleItemResponse(String weekday, LocalTime triggerTime, LocalTime limitTime,
                                               boolean custom, boolean active) {

        static WeekdayScheduleItemResponse from(DiaDeLaSemana d) {
            return new WeekdayScheduleItemResponse(d.diaSemana().name(), d.horaDisparo(), d.horaLimite(),
                    d.propio(), d.activo());
        }
    }
}
