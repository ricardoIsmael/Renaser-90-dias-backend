package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import jakarta.validation.constraints.NotBlank;

public record CrearConversacionDirectaRequest(@NotBlank String otherUserId) {
}
