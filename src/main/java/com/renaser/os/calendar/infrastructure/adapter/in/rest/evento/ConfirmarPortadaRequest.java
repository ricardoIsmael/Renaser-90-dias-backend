package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import jakarta.validation.constraints.NotBlank;

record ConfirmarPortadaRequest(@NotBlank String ruta) {
}
