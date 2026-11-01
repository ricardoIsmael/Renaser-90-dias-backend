package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import jakarta.validation.constraints.NotBlank;

/** {@code formation} (spec 024): solo hace falta cuando las dos personas comparten más de una formación (dos administradores); si no, se ignora. */
public record CrearConversacionDirectaRequest(@NotBlank String otherUserId, String formation) {

    public CrearConversacionDirectaRequest(String otherUserId) {
        this(otherUserId, null);
    }
}
