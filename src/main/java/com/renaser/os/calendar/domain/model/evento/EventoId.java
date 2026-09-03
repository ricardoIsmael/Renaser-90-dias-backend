package com.renaser.os.calendar.domain.model.evento;

import java.util.UUID;

/**
 * Identidad del agregado {@link Evento}. Valida y envuelve un UUID, pero <b>no lo genera</b>:
 * la generacion vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code EventoId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD §5.4.7: {@code domain/} sin aleatoriedad).
 */
public record EventoId(UUID value) {

    public EventoId {
        if (value == null) {
            throw new IllegalArgumentException("EventoId no puede ser null");
        }
    }

    public static EventoId of(UUID value) {
        return new EventoId(value);
    }

    public static EventoId of(String value) {
        return new EventoId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
