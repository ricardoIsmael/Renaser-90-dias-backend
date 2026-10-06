package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de {@code POST /attendance/close} y {@code /reopen} (D-256). */
record OcurrenciaRequest(@NotBlank String occurrenceStart) {
}
