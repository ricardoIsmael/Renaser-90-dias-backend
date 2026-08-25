package com.renaser.os.support.domain.model.ticketsoporte;

import java.util.UUID;

/** Identidad de un ticket de soporte tecnico (tabla `tickets_soporte`). */
public record TicketSoporteId(UUID value) {

    public TicketSoporteId {
        if (value == null) {
            throw new IllegalArgumentException("TicketSoporteId no puede ser null");
        }
    }

    public static TicketSoporteId of(UUID value) {
        return new TicketSoporteId(value);
    }

    public static TicketSoporteId newId() {
        return new TicketSoporteId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
