package com.renaser.os.support.application.ports.in.ticketmentor;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;

import java.time.Instant;
import java.util.List;

public interface ListarTicketsMentorUseCase {

    TicketsMentorPage propios(UserId actorId, Instant cursor);

    TicketsMentorPage todos(UserId actorId, Instant cursor);

    record TicketsMentorPage(List<TicketMentor> tickets, Instant nextCursor) {
    }
}
