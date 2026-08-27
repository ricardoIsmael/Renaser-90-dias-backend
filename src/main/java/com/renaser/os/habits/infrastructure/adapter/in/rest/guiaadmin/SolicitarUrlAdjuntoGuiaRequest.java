package com.renaser.os.habits.infrastructure.adapter.in.rest.guiaadmin;

import jakarta.validation.constraints.NotBlank;

public record SolicitarUrlAdjuntoGuiaRequest(@NotBlank String tipoContenido) {
}
