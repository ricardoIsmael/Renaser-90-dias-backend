package com.renaser.os.habits.domain.model.registro;

import java.time.Duration;
import java.time.Instant;

/**
 * Puntos que gana un habito segun el instante de entrega — traduccion literal
 * de `resolveHabitAward` (points.ts:100-125, docs/MODULO_POINTS.md §2.1):
 *
 * <pre>
 *   Entregado &lt;= instanteAncla                    -&gt; A_TIEMPO,  10 puntos
 *   instanteAncla &lt; entrega &lt;= +10 min             -&gt; GRACIA,    max(5, 10 - floor(min/2))
 *   +10 min &lt; entrega &lt;= +10 min + extension       -&gt; EXTENDIDO, 3 puntos fijos
 *   pasado eso                                      -&gt; EXPIRADO,  0 puntos (bloqueado)
 * </pre>
 */
public record ResultadoOtorgamiento(FaseOtorgamiento fase, int puntos) {

    public static final int PUNTOS_COMPLETOS = 10;
    public static final int GRACIA_PASO_MINUTOS = 2;
    public static final int GRACIA_PUNTOS_MINIMOS = 5;
    public static final int PUNTOS_EXTENSION = 3;

    /**
     * @param instanteAncla el {@link VentanaEntrega#instanteAncla()} de este habito
     * @param entregadoEn   cuando se marco completado o se subio la evidencia
     * @param extension     el {@link VentanaEntrega#extension()} ya recortado
     */
    public static ResultadoOtorgamiento calcular(Instant instanteAncla, Instant entregadoEn, Duration extension) {
        Duration tarde = Duration.between(instanteAncla, entregadoEn);
        if (!tarde.isPositive()) {
            return new ResultadoOtorgamiento(FaseOtorgamiento.A_TIEMPO, PUNTOS_COMPLETOS);
        }

        // El corte de fase se compara sobre el instante exacto (Duration), no sobre minutos
        // truncados: truncar antes de comparar clasificaba "+10min y 1seg" como todavia GRACIA
        // (floor(10:01) = 10 <= 10), violando el contrato documentado arriba ("entrega <= +10 min").
        Duration limiteGracia = Duration.ofMinutes(VentanaEntrega.GRACIA_MINUTOS);
        if (tarde.compareTo(limiteGracia) <= 0) {
            long minutosTarde = tarde.toMinutes();
            long descontados = minutosTarde / GRACIA_PASO_MINUTOS;
            int puntos = (int) Math.max(GRACIA_PUNTOS_MINIMOS, PUNTOS_COMPLETOS - descontados);
            return new ResultadoOtorgamiento(FaseOtorgamiento.GRACIA, puntos);
        }

        Duration limiteExtendido = limiteGracia.plus(extension);
        if (tarde.compareTo(limiteExtendido) <= 0) {
            return new ResultadoOtorgamiento(FaseOtorgamiento.EXTENDIDO, PUNTOS_EXTENSION);
        }

        return new ResultadoOtorgamiento(FaseOtorgamiento.EXPIRADO, 0);
    }
}
