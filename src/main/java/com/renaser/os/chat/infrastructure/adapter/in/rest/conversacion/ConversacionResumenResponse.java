package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.application.ports.in.conversacion.ListarConversacionesUseCase.ConversacionResumen;
import com.renaser.os.chat.infrastructure.adapter.in.rest.mensaje.MensajeResponse;

public record ConversacionResumenResponse(ConversacionResponse conversation, MensajeResponse lastMessage,
                                           long unreadCount) {

    public static ConversacionResumenResponse from(ConversacionResumen resumen) {
        return new ConversacionResumenResponse(ConversacionResponse.from(resumen.conversacion()),
                resumen.ultimoMensaje() != null ? MensajeResponse.from(resumen.ultimoMensaje()) : null,
                resumen.noLeidos());
    }
}
