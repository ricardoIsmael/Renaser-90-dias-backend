package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * Cuerpo de {@code PUT /api/v1/habit-preferences/{habitId}/weekdays/{weekday}} — "los lunes a las 5".
 *
 * <p>{@code triggerTime} obligatorio: una fila sin hora de disparo no dice nada que la preferencia
 * general no diga ya. Para volver al horario general se usa el DELETE, no un cuerpo vacio.
 *
 * <p>{@code limitTime} opcional, como en el resto del modulo: la mayoria de los habitos no vence
 * dentro del dia. En {@code null} ese dia hereda la hora limite general.
 */
public record WeekdayScheduleRequest(@NotNull LocalTime triggerTime, LocalTime limitTime) {
}
