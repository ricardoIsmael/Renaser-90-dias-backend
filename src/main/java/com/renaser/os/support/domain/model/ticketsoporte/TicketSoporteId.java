package com.renaser.os.support.domain.model.ticketsoporte;

import java.util.UUID;

/**
 * Identidad de un ticket de soporte tecnico (tabla `tickets_soporte`). Valida y envuelve un UUID,
 * pero <b>no lo genera</b>: la generacion vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code TicketSoporteId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD §5.4.7: {@code domain/} sin aleatoriedad).
 */
public record TicketSoporteId(UUID value) {

    public TicketSoporteId {
        if (value == null) {
            throw new IllegalArgumentException("TicketSoporteId no puede ser null");
        }
    }

    public static TicketSoporteId of(UUID value) {
        return new TicketSoporteId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
