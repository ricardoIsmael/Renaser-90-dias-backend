package com.renaser.os.habits.infrastructure.adapter.in.rest.audioterapiaadmin;

import jakarta.validation.constraints.Positive;

public record UpdateAudioTherapyDurationRequest(@Positive Integer durationDays) {
}
