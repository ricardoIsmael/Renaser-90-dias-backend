package com.renaser.os.habits.infrastructure.adapter.in.rest.horarioadmin;

import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/** Espejo de {@code CreateScheduleInput}. */
public record CreateScheduleRequest(int startDay, Integer endDay, @NotNull DayTypeDto dayType,
                                     LocalTime defaultTriggerTime, LocalTime defaultLimitTime) {
}
