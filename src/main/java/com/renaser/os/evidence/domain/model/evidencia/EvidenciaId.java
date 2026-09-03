package com.renaser.os.evidence.domain.model.evidencia;

import java.util.UUID;

/**
 * Identidad de una Evidencia (tabla {@code evidencias}). Valida y envuelve un UUID, pero
 * <b>no lo genera</b>: la generacion vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code EvidenciaId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD sec. 5.4.7: {@code domain/} sin aleatoriedad).
 */
public record EvidenciaId(UUID value) {

    public EvidenciaId {
        if (value == null) {
            throw new IllegalArgumentException("EvidenciaId no puede ser null");
        }
    }

    public static EvidenciaId of(UUID value) {
        return new EvidenciaId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
