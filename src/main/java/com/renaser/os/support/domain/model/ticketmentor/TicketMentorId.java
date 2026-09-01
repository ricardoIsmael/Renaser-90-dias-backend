package com.renaser.os.support.domain.model.ticketmentor;

import java.util.UUID;

/**
 * Identidad de un ticket de mentoria (tabla `tickets_mentor`). Valida y envuelve un UUID, pero <b>no lo genera</b>:
 * la generacion vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code TicketMentorId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD §5.4.7: {@code domain/} sin aleatoriedad).
 */
public record TicketMentorId(UUID value) {

    public TicketMentorId {
        if (value == null) {
            throw new IllegalArgumentException("TicketMentorId no puede ser null");
        }
    }

    public static TicketMentorId of(UUID value) {
        return new TicketMentorId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
