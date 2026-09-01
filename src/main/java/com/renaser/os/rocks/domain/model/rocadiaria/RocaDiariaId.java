package com.renaser.os.rocks.domain.model.rocadiaria;

import java.util.UUID;

/**
 * Identidad de una Roca Diaria (tabla `rocas_diarias`).
 *
 * <p>No genera el UUID: la generacion vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code RocaDiariaId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * ({@code RocaDiariaService.crear}). CLAUDE.MD §5.4.7: {@code domain/} sin aleatoriedad.
 */
public record RocaDiariaId(UUID value) {

    public RocaDiariaId {
        if (value == null) {
            throw new IllegalArgumentException("RocaDiariaId no puede ser null");
        }
    }

    public static RocaDiariaId of(UUID value) {
        return new RocaDiariaId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
