package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Cuerpo de {@code PUT /api/v1/habit-preferences/{habitId}/weekdays/{weekday}/reminder}: el aviso de un
 * dia de la semana (spec 008, D-282).
 *
 * <p>{@code reminderEnabled} obligatorio y {@code Boolean}, no {@code boolean}: omitirlo es un 400 de
 * validacion y no un {@code false} que apagaria el aviso sin que nadie lo pida (el bug del 2026-09-03).
 * {@code reminderMinutesList} lleva las antelaciones en minutos cuando el aviso esta activo; con
 * {@code false} se ignora y puede faltar o venir vacia. Que un aviso activo tenga al menos una y que esten
 * en rango lo valida el dominio (400).
 */
public record WeekdayReminderRequest(@NotNull Boolean reminderEnabled, List<Integer> reminderMinutesList) {
}
