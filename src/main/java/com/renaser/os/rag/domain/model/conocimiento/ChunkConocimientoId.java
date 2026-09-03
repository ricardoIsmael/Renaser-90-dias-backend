package com.renaser.os.rag.domain.model.conocimiento;

import java.util.UUID;

/**
 * Identidad de un ChunkConocimiento (tabla {@code base_conocimiento}). Valida y envuelve un
 * UUID, pero <b>no lo genera</b>: la generacion vive fuera de {@code domain/}, detras del
 * puerto {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code ChunkConocimientoId.of(idGenerator.newId())} antes de invocar la factoria del
 * agregado (CLAUDE.MD sec. 5.4.7: {@code domain/} sin aleatoriedad).
 */
public record ChunkConocimientoId(UUID value) {

    public ChunkConocimientoId {
        if (value == null) {
            throw new IllegalArgumentException("ChunkConocimientoId no puede ser null");
        }
    }

    public static ChunkConocimientoId of(UUID value) {
        return new ChunkConocimientoId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
