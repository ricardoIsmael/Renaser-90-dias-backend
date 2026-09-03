package com.renaser.os.rocks.domain.model.verdugo;

import java.util.UUID;

/**
 * Identidad de un Evento Verdugo (tabla `eventos_verdugo`).
 *
 * <p>No genera el UUID: la generacion vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code EventoVerdugoId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * ({@code VerdugoService.registrar}). CLAUDE.MD §5.4.7: {@code domain/} sin aleatoriedad.
 */
public record EventoVerdugoId(UUID value) {

    public EventoVerdugoId {
        if (value == null) {
            throw new IllegalArgumentException("EventoVerdugoId no puede ser null");
        }
    }

    public static EventoVerdugoId of(UUID value) {
        return new EventoVerdugoId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
