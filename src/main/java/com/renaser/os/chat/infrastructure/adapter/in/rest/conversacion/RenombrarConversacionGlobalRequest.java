package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import jakarta.validation.constraints.NotBlank;

/** {@code formation} (spec 024): la comunidad general que se renombra (hay una por formación); ausente = la de la Escuela. */
public record RenombrarConversacionGlobalRequest(@NotBlank String name, String formation) {

    public RenombrarConversacionGlobalRequest(String name) {
        this(name, null);
    }
}
