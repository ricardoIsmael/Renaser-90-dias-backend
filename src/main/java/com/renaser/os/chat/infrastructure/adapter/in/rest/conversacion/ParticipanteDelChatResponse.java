package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.application.ports.in.conversacion.VerParticipantesDeConversacionUseCase.ParticipanteDelChat;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;

/**
 * Una persona en la lista de integrantes. Sin correo ni teléfono. {@code fotoPath} es la ruta de su tarjeta
 * con nombre (D-206), relativa a la API, en los chats que las tienen (grupo y soporte); {@code avatarUrl}, la
 * foto que subió, para los demás.
 */
public record ParticipanteDelChatResponse(String userId, String nombre, String rol, boolean esUnoMismo,
                                          String fotoPath, String avatarUrl) {

    static ParticipanteDelChatResponse from(ConversacionId conversacion, ParticipanteDelChat p) {
        String ruta = p.llevaTarjeta()
                ? FotosDelChatController.rutaDeLaFotoDeIntegrante(conversacion, p.userId()) : null;
        return new ParticipanteDelChatResponse(p.userId().toString(), p.nombre(), p.rol().name(), p.esUnoMismo(),
                ruta, p.avatarUrl());
    }
}
