package com.renaser.os.rocks.domain.model.rocadiaria;

/**
 * Color Pareto de una Roca Diaria — posición 1 = VERDE (la más importante del
 * eje ese día), 2 = AMARILLA, 3 = ROJA. Espejo del tipo Postgres `color_pareto`
 * (antes `ParetoColor.GREEN/YELLOW/RED`).
 */
public enum ColorPareto {
    VERDE,
    AMARILLA,
    ROJA;

    /** El color que corresponde a cada posición (1..3) — regla dura, no configurable. */
    public static ColorPareto paraPosicion(int posicion) {
        return switch (posicion) {
            case 1 -> VERDE;
            case 2 -> AMARILLA;
            case 3 -> ROJA;
            default -> throw new IllegalArgumentException("posicion debe ser 1, 2 o 3: " + posicion);
        };
    }
}
