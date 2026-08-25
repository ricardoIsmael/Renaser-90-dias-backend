package com.renaser.os.rocks.domain.model.rocadiaria;

import java.time.Duration;
import java.time.Instant;

/**
 * Escala de puntos al completar una Roca Diaria — MISMA escala exacta que
 * `habits` usa para hábitos (`resolveHabitAward` en el repo viejo,
 * `src/features/habits/points.ts:112-117`, portada también en
 * `docs/MODULO_POINTS.md` §2.1). No se reimplementa aparte por casualidad: el
 * comentario del repo viejo (`rocks/service.ts:459-468`) es explícito en que
 * es un espejo deliberado de la fórmula de hábitos, para que las dos escalas
 * no puedan divergir con el tiempo.
 *
 * <pre>
 *   completada a horaFin o antes         10 puntos
 *   cada 2 min de retraso (hasta 10 min) -1, con piso en 5
 *   pasada la gracia, hasta +3 h          3 puntos fijos
 *   mas tarde                             0 (EXPIRADO)
 *   sin horaFin                          10 (no hay plazo al que llegar tarde)
 * </pre>
 *
 * <p><b>RK-4:</b> a diferencia de un hábito (que puede traer su propio
 * `evidenceExtensionHours`), una Roca Diaria no tiene columna de extensión
 * configurable en el baseline — se usa el default fijo de hábitos (3 h).
 */
public final class EscalaPuntosRoca {

    public static final int PUNTOS_A_TIEMPO = 10;
    public static final Duration VENTANA_GRACIA = Duration.ofMinutes(10);
    private static final int PASO_GRACIA_MINUTOS = 2;
    private static final int PUNTOS_MIN_GRACIA = 5;
    public static final Duration VENTANA_EXTENSION = Duration.ofHours(3);
    public static final int PUNTOS_EXTENSION = 3;

    private EscalaPuntosRoca() {
    }

    /** {@code horaFin} nulo = sin plazo: siempre {@link FasePremio#A_TIEMPO}, 10 puntos completos. */
    public static ResultadoPremio calcular(Instant horaFin, Instant completadaEn) {
        if (horaFin == null || !completadaEn.isAfter(horaFin)) {
            return new ResultadoPremio(FasePremio.A_TIEMPO, PUNTOS_A_TIEMPO);
        }
        long minutosTarde = Duration.between(horaFin, completadaEn).toMinutes();
        if (minutosTarde <= VENTANA_GRACIA.toMinutes()) {
            int puntos = Math.max(PUNTOS_MIN_GRACIA, PUNTOS_A_TIEMPO - (int) (minutosTarde / PASO_GRACIA_MINUTOS));
            return new ResultadoPremio(FasePremio.GRACIA, puntos);
        }
        Instant limiteExtension = horaFin.plus(VENTANA_GRACIA).plus(VENTANA_EXTENSION);
        if (!completadaEn.isAfter(limiteExtension)) {
            return new ResultadoPremio(FasePremio.EXTENDIDO, PUNTOS_EXTENSION);
        }
        return new ResultadoPremio(FasePremio.EXPIRADO, 0);
    }
}
