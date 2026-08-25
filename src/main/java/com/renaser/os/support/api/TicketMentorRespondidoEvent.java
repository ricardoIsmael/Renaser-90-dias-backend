package com.renaser.os.support.api;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.event.DomainEvent;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentorId;

import java.time.Instant;

public record TicketMentorRespondidoEvent(TicketMentorId ticketId, UserId participanteId,
                                           Instant occurredAt) implements DomainEvent {
}
