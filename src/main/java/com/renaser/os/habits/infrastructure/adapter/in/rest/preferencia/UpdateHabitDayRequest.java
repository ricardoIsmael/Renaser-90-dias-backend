package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de {@code PATCH /api/v1/habit-preferences/{habitId}/days/{date}} — el interruptor de UN dia.
 *
 * <p>{@code Boolean} y no {@code boolean}: un primitivo obliga a Jackson a tener el campo si o si, y
 * cuando falta no se cae la validacion sino la construccion entera del DTO — 400 opaco
 * ("El cuerpo de la solicitud es invalido") para cualquier cliente que lo omita. Es el bug real que
 * `UpdateHabitPreferenceRequest.reminderEnabled` provoco el 2026-09-03 y que esta anotado en
 * `habitsApi.ts` del movil. Con el wrapper, omitirlo devuelve el 400 de validacion que corresponde,
 * diciendo cual es el campo.
 */
public record UpdateHabitDayRequest(@NotNull Boolean active) {
}
