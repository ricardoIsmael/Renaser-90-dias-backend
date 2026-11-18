package com.renaser.os.support.application.ports.in.ticketmentor;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface ListarTicketsMentorUseCase {

    TicketsMentorPage propios(UserId actorId, Instant cursor);

    TicketsMentorPage todos(UserId actorId, Instant cursor);

    /**
     * @param nombresDeQuienRespondio el nombre de cada persona que respondió alguno de estos tickets (spec 056, FR-030), en una sola lectura por página;
     *                                quien no está (cuenta borrada) simplemente no aparece
     */
    record TicketsMentorPage(List<TicketMentor> tickets, Instant nextCursor, Map<UserId, String> nombresDeQuienRespondio) {

        public TicketsMentorPage(List<TicketMentor> tickets, Instant nextCursor) {
            this(tickets, nextCursor, Map.of());
        }
    }
}
