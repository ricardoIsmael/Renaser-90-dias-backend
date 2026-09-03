package com.renaser.os.rocks.domain.model.rocasemanal;

import java.util.UUID;

/**
 * Identidad de una Roca Semanal (tabla `rocas_semanales`).
 *
 * <p>No genera el UUID: la generacion vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code RocaSemanalId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * ({@code RocaSemanalService.crear}). CLAUDE.MD §5.4.7: {@code domain/} sin aleatoriedad.
 */
public record RocaSemanalId(UUID value) {

    public RocaSemanalId {
        if (value == null) {
            throw new IllegalArgumentException("RocaSemanalId no puede ser null");
        }
    }

    public static RocaSemanalId of(UUID value) {
        return new RocaSemanalId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
