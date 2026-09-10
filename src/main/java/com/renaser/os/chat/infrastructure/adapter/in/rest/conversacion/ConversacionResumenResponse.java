package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.application.ports.in.conversacion.ListarConversacionesUseCase.ConversacionResumen;
import com.renaser.os.chat.infrastructure.adapter.in.rest.mensaje.MensajeResponse;

/**
 * @param otherParticipantId con quien es el chat, cuando es 1 a 1. {@code null} en grupos. El
 * @param otherParticipantName su nombre, ya resuelto. NO se deja que el cliente lo busque en
 *                             {@code GET /chat/members}: ese directorio exige que exista la
 *                             conversacion GLOBAL y sin ella responde 404.
 */
public record ConversacionResumenResponse(ConversacionResponse conversation, MensajeResponse lastMessage,
                                           long unreadCount, String otherParticipantId,
                                           String otherParticipantName, String otherParticipantAvatarUrl) {

    public static ConversacionResumenResponse from(ConversacionResumen resumen) {
        return new ConversacionResumenResponse(ConversacionResponse.from(resumen.conversacion()),
                resumen.ultimoMensaje() != null ? MensajeResponse.from(resumen.ultimoMensaje()) : null,
                resumen.noLeidos(),
                resumen.otroParticipante() != null ? resumen.otroParticipante().value().toString() : null,
                resumen.otroParticipanteNombre(), resumen.otroParticipanteAvatar());
    }
}
