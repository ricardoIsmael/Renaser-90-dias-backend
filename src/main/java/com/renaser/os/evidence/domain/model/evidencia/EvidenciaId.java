package com.renaser.os.evidence.domain.model.evidencia;

import java.util.UUID;

/** Identidad de una Evidencia (tabla {@code evidencias}). */
public record EvidenciaId(UUID value) {

    public EvidenciaId {
        if (value == null) {
            throw new IllegalArgumentException("EvidenciaId no puede ser null");
        }
    }

    public static EvidenciaId of(UUID value) {
        return new EvidenciaId(value);
    }

    public static EvidenciaId newId() {
        return new EvidenciaId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
