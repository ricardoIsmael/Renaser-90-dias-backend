package com.renaser.os.points.domain.model.semaforo;

import com.renaser.os.points.api.ColorSemaforo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Optional;

/**
 * La fórmula del semáforo de cumplimiento del aprendiz, en un solo lugar (D-168, confirmada por el
 * dueño el 2026-09-25):
 * <ul>
 *   <li>% del día = cumplidos ÷ programados × 100, redondeado a entero mitad hacia arriba (el
 *       {@code Math.round} del {@code coherence.ts} viejo, para positivos);</li>
 *   <li>semáforo = promedio de los % de los días con algo programado, con 1 decimal (el doble
 *       redondeo de {@code coherence.ts:97,130});</li>
 *   <li>verde ≥ 80, amarillo ≥ 60, rojo por debajo; sin días con datos, SIN_DATOS (nunca verde).</li>
 * </ul>
 * Aritmética en {@link BigDecimal} para que la mitad exacta (12,5 %) redondee siempre igual.
 */
public final class ReglaDelSemaforo {

    public static final int UMBRAL_VERDE = 80;
    public static final int UMBRAL_AMARILLO = 60;

    /** Con qué reglas se tomó una foto semanal: permite recalcular a propósito si cambian. */
    public static final String VERSION_FORMULA = "2026-09-25-promedio-de-dias";

    private static final BigDecimal VERDE = BigDecimal.valueOf(UMBRAL_VERDE);
    private static final BigDecimal AMARILLO = BigDecimal.valueOf(UMBRAL_AMARILLO);
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private ReglaDelSemaforo() {
    }

    /** Solo para días con algo programado: un día vacío no tiene porcentaje, ni 0 ni 100. */
    public static int porcentajeDelDia(int cumplidos, int programados) {
        if (programados <= 0) {
            throw new IllegalArgumentException("Un dia sin nada programado no tiene porcentaje");
        }
        if (cumplidos < 0 || cumplidos > programados) {
            throw new IllegalArgumentException("cumplidos fuera de rango: " + cumplidos + "/" + programados);
        }
        return BigDecimal.valueOf(cumplidos).multiply(CIEN)
                .divide(BigDecimal.valueOf(programados), 0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    /** Vacío si no hubo ningún día con datos: la ausencia de dato no se disfraza de número. */
    public static Optional<BigDecimal> promedio(Collection<Integer> porcentajesDeDias) {
        if (porcentajesDeDias.isEmpty()) {
            return Optional.empty();
        }
        long suma = porcentajesDeDias.stream().mapToLong(Integer::longValue).sum();
        return Optional.of(BigDecimal.valueOf(suma)
                .divide(BigDecimal.valueOf(porcentajesDeDias.size()), 1, RoundingMode.HALF_UP));
    }

    public static ColorSemaforo colorDe(BigDecimal porcentaje) {
        if (porcentaje == null) {
            return ColorSemaforo.SIN_DATOS;
        }
        if (porcentaje.compareTo(VERDE) >= 0) {
            return ColorSemaforo.VERDE;
        }
        return porcentaje.compareTo(AMARILLO) >= 0 ? ColorSemaforo.AMARILLO : ColorSemaforo.ROJO;
    }

    public static ColorSemaforo colorDelDia(int porcentaje) {
        return colorDe(BigDecimal.valueOf(porcentaje));
    }
}
