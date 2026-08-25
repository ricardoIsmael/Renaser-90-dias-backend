package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import jakarta.validation.constraints.NotBlank;

record RsvpRequest(@NotBlank String occurrenceStart, @NotBlank String status) {
}
