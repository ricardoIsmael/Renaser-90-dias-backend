package com.renaser.os.habits.domain.model.registro;

import java.time.Duration;
import java.time.Instant;

/**
 * Puntos que gana un habito segun el instante de entrega.
 *
 * <p><b>D-97 (2026-09-04): el orden de los tramos se INVIRTIO por decision del dueno.</b>
 * La traduccion 1:1 de `resolveHabitAward` (points.ts:100-125) ponia primero los 10 minutos
 * de decaimiento y despues 3 horas planas de 3 puntos. El dueno definio lo contrario, textual:
 * "3h a 10 puntos y luego, dentro de los 10 minutos, cada 2 minutos se resta 1 punto, en total
 * ganando 5":
 *
 * <pre>
 *   Entregado &lt;= instanteAncla                    -&gt; A_TIEMPO,  10 puntos
 *   instanteAncla &lt; entrega &lt;= +extension (3 h)    -&gt; EXTENDIDO, 10 puntos (puntaje completo)
 *   +extension &lt; entrega &lt;= +extension + 10 min    -&gt; GRACIA,    max(5, 10 - floor(min/2))
 *   pasado eso                                      -&gt; EXPIRADO,  0 puntos (bloqueado)
 * </pre>
 *
 * <p>La duracion total del plazo ({@link VentanaEntrega#plazoEvidencia()}) no cambia — sigue
 * siendo ancla + extension + 10 min —, asi que el corte de expiracion y el barrido nocturno no
 * se enteran. Solo cambia cuanto vale cada tramo. {@code EXTENDIDO} sigue existiendo como fase
 * (y como {@code MotivoPuntos.HABIT_EXTENDED}) porque sigue siendo verdad que la entrega fue
 * fuera de hora; lo que ya no es verdad es que eso cueste puntos.
 */
public record ResultadoOtorgamiento(FaseOtorgamiento fase, int puntos) {

    public static final int PUNTOS_COMPLETOS = 10;
    public static final int GRACIA_PASO_MINUTOS = 2;
    public static final int GRACIA_PUNTOS_MINIMOS = 5;

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

        // D-97: primero la extension (3 h por defecto) a puntaje COMPLETO...
        if (tarde.compareTo(extension) <= 0) {
            return new ResultadoOtorgamiento(FaseOtorgamiento.EXTENDIDO, PUNTOS_COMPLETOS);
        }

        // ...y recien despues los 10 minutos de gracia, decayendo de 10 a 5. Los minutos que
        // descuentan son los que pasaron DESDE que termino la extension, no desde el ancla.
        //
        // El corte de fase se compara sobre el instante exacto (Duration), no sobre minutos
        // truncados: truncar antes de comparar clasificaba "+10min y 1seg" como todavia GRACIA
        // (floor(10:01) = 10 <= 10), violando el contrato documentado arriba ("entrega <= +10 min").
        Duration enGracia = tarde.minus(extension);
        Duration limiteGracia = Duration.ofMinutes(VentanaEntrega.GRACIA_MINUTOS);
        if (enGracia.compareTo(limiteGracia) <= 0) {
            long descontados = enGracia.toMinutes() / GRACIA_PASO_MINUTOS;
            int puntos = (int) Math.max(GRACIA_PUNTOS_MINIMOS, PUNTOS_COMPLETOS - descontados);
            return new ResultadoOtorgamiento(FaseOtorgamiento.GRACIA, puntos);
        }

        return new ResultadoOtorgamiento(FaseOtorgamiento.EXPIRADO, 0);
    }
}
