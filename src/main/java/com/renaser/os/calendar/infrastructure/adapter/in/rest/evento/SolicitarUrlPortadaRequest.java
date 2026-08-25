package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import jakarta.validation.constraints.NotBlank;

record SolicitarUrlPortadaRequest(@NotBlank String contentType) {
}
