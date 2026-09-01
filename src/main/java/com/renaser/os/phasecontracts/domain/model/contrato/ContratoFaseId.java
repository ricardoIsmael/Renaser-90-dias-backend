package com.renaser.os.phasecontracts.domain.model.contrato;

import java.util.UUID;

/**
 * Identidad de un pacto de fase firmado (tabla `contratos_fase`). Valida y envuelve un UUID,
 * pero <b>no lo genera</b>: la generacion vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code ContratoFaseId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD §5.4.7: {@code domain/} sin aleatoriedad).
 */
public record ContratoFaseId(UUID value) {

    public ContratoFaseId {
        if (value == null) {
            throw new IllegalArgumentException("ContratoFaseId no puede ser null");
        }
    }

    public static ContratoFaseId of(UUID value) {
        return new ContratoFaseId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
