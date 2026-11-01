package com.renaser.os.rag.domain.model.analisis;

/** Lo que cubre un análisis 80/20 (spec 031): una semana o un mes del programa. Espejo de la columna {@code analisis_80_20.periodo}. */
public enum TipoDePeriodo {
    SEMANA,
    MES;

    /** @throws IllegalArgumentException si no es ninguno de los dos (400) */
    public static TipoDePeriodo desde(String valor) {
        for (TipoDePeriodo tipo : values()) {
            if (tipo.name().equalsIgnoreCase(valor == null ? "" : valor.trim())) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("El periodo es SEMANA o MES");
    }
}
