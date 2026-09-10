package com.renaser.os.points.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * El porcentaje de un alumno dentro de la ventana evaluada. Se expone porque el mentor tiene
 * derecho a ver de dónde sale su nota; la evaluación histórica agregada, en cambio, no lleva
 * estos detalles (P-07).
 */
public record CumplimientoAprendiz(UUID aprendizId, int entregadas, int esperadas, int tardiasFueraDeVentana,
                                    BigDecimal porcentaje) {

    /** Sin obligaciones vencidas no entra al promedio del mentor. */
    public boolean evaluable() {
        return esperadas > 0;
    }
}
