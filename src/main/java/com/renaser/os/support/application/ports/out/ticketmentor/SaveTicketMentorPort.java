package com.renaser.os.support.application.ports.out.ticketmentor;

import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;

public interface SaveTicketMentorPort {

    TicketMentor save(TicketMentor ticket);
}
