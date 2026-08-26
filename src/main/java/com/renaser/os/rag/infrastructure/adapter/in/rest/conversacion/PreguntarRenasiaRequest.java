package com.renaser.os.rag.infrastructure.adapter.in.rest.conversacion;

import jakarta.validation.constraints.NotBlank;

public record PreguntarRenasiaRequest(@NotBlank String question) {
}
