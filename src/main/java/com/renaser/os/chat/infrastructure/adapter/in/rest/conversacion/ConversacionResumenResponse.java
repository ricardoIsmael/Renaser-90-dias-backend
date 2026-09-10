package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.application.ports.in.conversacion.ListarConversacionesUseCase.ConversacionResumen;
import com.renaser.os.chat.infrastructure.adapter.in.rest.mensaje.MensajeResponse;

/**
 * @param otherParticipantId con quien es el chat, cuando es 1 a 1. {@code null} en grupos. El
 *                           cliente resuelve el nombre contra el directorio que ya pide
 *                           ({@code GET /chat/members}); aca va solo el id para no repetir la
 *                           resolucion de nombres en dos sitios.
 */
public record ConversacionResumenResponse(ConversacionResponse conversation, MensajeResponse lastMessage,
                                           long unreadCount, String otherParticipantId) {

    public static ConversacionResumenResponse from(ConversacionResumen resumen) {
        return new ConversacionResumenResponse(ConversacionResponse.from(resumen.conversacion()),
                resumen.ultimoMensaje() != null ? MensajeResponse.from(resumen.ultimoMensaje()) : null,
                resumen.noLeidos(),
                resumen.otroParticipante() != null ? resumen.otroParticipante().value().toString() : null);
    }
}
