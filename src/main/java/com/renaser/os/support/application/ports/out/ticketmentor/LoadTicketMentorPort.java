package com.renaser.os.support.application.ports.out.ticketmentor;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentorId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoadTicketMentorPort {

    Optional<TicketMentor> byId(TicketMentorId id);

    /** Ordenado por creadoEn desc. cursor null = desde el principio. */
    List<TicketMentor> porParticipante(UserId participanteId, Instant cursor, int limite);

    /** Ordenado por creadoEn desc, sin filtrar por participante — vista de mentor/admin. */
    List<TicketMentor> todos(Instant cursor, int limite);
}
