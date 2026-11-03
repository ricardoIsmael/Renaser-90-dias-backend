package com.renaser.os.rag.domain.model.analisis;

import java.util.UUID;

/** Identidad de un análisis 80/20 ({@code analisis_80_20.id}). Valida y envuelve un UUID; no lo genera: entra por {@code IdGenerator}. */
public record AnalisisDelPeriodoId(UUID value) {

    public AnalisisDelPeriodoId {
        if (value == null) {
            throw new IllegalArgumentException("AnalisisDelPeriodoId no puede ser null");
        }
    }

    public static AnalisisDelPeriodoId of(UUID value) {
        return new AnalisisDelPeriodoId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
