package com.renaser.os.rag.domain.model.conversacion;

import java.util.UUID;

/**
 * Identidad de un mensaje de Renasia (tabla `mensajes_renasia`). Valida y envuelve un UUID,
 * pero <b>no lo genera</b>: la generacion vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code MensajeRenasiaId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD sec. 5.4.7: {@code domain/} sin aleatoriedad).
 */
public record MensajeRenasiaId(UUID value) {

    public MensajeRenasiaId {
        if (value == null) {
            throw new IllegalArgumentException("MensajeRenasiaId no puede ser null");
        }
    }

    public static MensajeRenasiaId of(UUID value) {
        return new MensajeRenasiaId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
