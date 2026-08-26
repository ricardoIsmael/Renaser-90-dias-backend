package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/** Nombres de campo en ingles: contrato HTTP viejo literal (D-36) — {@code PATCH .../habit-preferences/{habitId}}. */
public record UpdateHabitPreferenceRequest(@NotNull LocalTime triggerTime, @NotNull LocalTime limitTime,
                                            boolean reminderEnabled, Integer reminderMinutesBefore) {
}
