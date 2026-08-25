package com.renaser.os.support.domain.model.ticketmentor;

import java.util.UUID;

/** Identidad de un ticket de mentoria (tabla `tickets_mentor`). */
public record TicketMentorId(UUID value) {

    public TicketMentorId {
        if (value == null) {
            throw new IllegalArgumentException("TicketMentorId no puede ser null");
        }
    }

    public static TicketMentorId of(UUID value) {
        return new TicketMentorId(value);
    }

    public static TicketMentorId newId() {
        return new TicketMentorId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
