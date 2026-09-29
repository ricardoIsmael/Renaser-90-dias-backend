package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.application.ports.in.conversacion.VerParticipantesDeConversacionUseCase.PaginaDeParticipantes;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;

import java.util.List;

/** La página de integrantes y cuántos son en total (con la búsqueda aplicada). */
public record ParticipantesDelChatResponse(List<ParticipanteDelChatResponse> participants, int total, int page,
                                           int size) {

    static ParticipantesDelChatResponse from(ConversacionId conversacion, PaginaDeParticipantes pagina) {
        return new ParticipantesDelChatResponse(
                pagina.participantes().stream().map(p -> ParticipanteDelChatResponse.from(conversacion, p)).toList(),
                pagina.total(), pagina.pagina(), pagina.tamano());
    }
}
