package com.renaser.os.support.application.services;

import com.renaser.os.support.api.ConsultaDeTicketFinder;
import com.renaser.os.support.application.ports.out.ticketmentor.LoadTicketMentorPort;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentorId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Implementación de {@link ConsultaDeTicketFinder}. Solo lectura y sin autorización propia: quien la consume (el líder de mentores) ya decidió. */
@Service
public class ConsultaDeTicketFinderService implements ConsultaDeTicketFinder {

    private final LoadTicketMentorPort tickets;

    public ConsultaDeTicketFinderService(LoadTicketMentorPort tickets) {
        this.tickets = tickets;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConsultaDeTicket> consulta(UUID ticketId) {
        return tickets.byId(TicketMentorId.of(ticketId)).map(ticket -> new ConsultaDeTicket(ticket.id().value(), ticket.participanteId(),
                ticket.descripcionBloqueo(), ticket.creadoEn(), ticket.estado().estaRespondido()));
    }
}
