package com.renaser.os.rag.domain.model.conocimiento;

import java.util.UUID;

/** Identidad de un ChunkConocimiento (tabla {@code base_conocimiento}). */
public record ChunkConocimientoId(UUID value) {

    public ChunkConocimientoId {
        if (value == null) {
            throw new IllegalArgumentException("ChunkConocimientoId no puede ser null");
        }
    }

    public static ChunkConocimientoId of(UUID value) {
        return new ChunkConocimientoId(value);
    }

    public static ChunkConocimientoId newId() {
        return new ChunkConocimientoId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
