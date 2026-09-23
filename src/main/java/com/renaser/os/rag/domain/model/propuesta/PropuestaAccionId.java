package com.renaser.os.rag.domain.model.propuesta;

import java.util.UUID;

/**
 * Identidad de una propuesta del acompanante (tabla {@code propuestas_acompanante}). Envuelve un
 * UUID pero no lo genera: lo arma el caso de uso con {@code IdGenerator} (CLAUDE.MD sec. 5.4.7).
 */
public record PropuestaAccionId(UUID value) {

    public PropuestaAccionId {
        if (value == null) {
            throw new IllegalArgumentException("PropuestaAccionId no puede ser null");
        }
    }

    public static PropuestaAccionId of(UUID value) {
        return new PropuestaAccionId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
