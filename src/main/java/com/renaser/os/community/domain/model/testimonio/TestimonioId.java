package com.renaser.os.community.domain.model.testimonio;

import java.util.UUID;

/**
 * Identidad de un testimonio (tabla `testimonios`). Valida y envuelve un UUID, pero <b>no lo
 * genera</b>: la generacion vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code TestimonioId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD sec. 5.4.7: {@code domain/} es puro, sin aleatoriedad).
 */
public record TestimonioId(UUID value) {

    public TestimonioId {
        if (value == null) {
            throw new IllegalArgumentException("TestimonioId no puede ser null");
        }
    }

    public static TestimonioId of(UUID value) {
        return new TestimonioId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
