package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import jakarta.validation.constraints.NotBlank;

record CancelarOcurrenciaRequest(@NotBlank String occurrenceStart) {
}
