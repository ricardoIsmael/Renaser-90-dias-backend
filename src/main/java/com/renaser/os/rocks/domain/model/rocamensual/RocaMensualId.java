package com.renaser.os.rocks.domain.model.rocamensual;

import java.util.UUID;

/**
 * Identidad de una Roca Mensual (tabla {@code rocas_mensuales}).
 *
 * <p>No genera el UUID: la generacion vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}. Mismo criterio que
 * {@code RocaSemanalId} (CLAUDE.MD §5.4.7: {@code domain/} sin aleatoriedad).
 */
public record RocaMensualId(UUID value) {

    public RocaMensualId {
        if (value == null) {
            throw new IllegalArgumentException("RocaMensualId no puede ser null");
        }
    }

    public static RocaMensualId of(UUID value) {
        return new RocaMensualId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
