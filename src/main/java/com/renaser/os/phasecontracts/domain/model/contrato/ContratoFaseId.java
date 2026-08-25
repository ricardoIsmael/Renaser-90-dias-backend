package com.renaser.os.phasecontracts.domain.model.contrato;

import java.util.UUID;

/** Identidad de un pacto de fase firmado (tabla `contratos_fase`). */
public record ContratoFaseId(UUID value) {

    public ContratoFaseId {
        if (value == null) {
            throw new IllegalArgumentException("ContratoFaseId no puede ser null");
        }
    }

    public static ContratoFaseId of(UUID value) {
        return new ContratoFaseId(value);
    }

    public static ContratoFaseId newId() {
        return new ContratoFaseId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
