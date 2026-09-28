package com.renaser.os.onboarding.domain.model.caja;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * El requisito de la Fase 1 para la caja (D-219, supuesto a confirmar con el dueño; spec §2): el
 * cumplimiento de hábitos de los días 1 a 7, calculado con la MISMA fórmula que el semáforo (D-168,
 * {@code ReglaDelSemaforo}): % de cada día = cumplidos ÷ programados × 100 redondeado a entero (mitad hacia
 * arriba), y el cumplimiento = promedio de los días con algo programado, con un decimal. Pasa sola a
 * {@link EstadoCaja#POR_REVISAR} con {@value #UMBRAL} o más, el umbral del verde.
 *
 * <p>El número vive solo acá: si el dueño lo cambia, se cambia esta constante.
 */
public final class CumplimientoFaseUno {

    /** El mismo umbral del verde del semáforo (D-168/D-181). */
    public static final int UMBRAL = 80;
    /** Días 1 a 7: la Fase 1 que se mide. */
    public static final int DIAS_MEDIDOS = 7;

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private CumplimientoFaseUno() {
    }

    /**
     * @param primeraFecha la fecha local del Día 1 del aprendiz
     * @param dias         sus días con registros (los de fuera de los días 1 a 7 se ignoran)
     * @return vacío si no tuvo nada programado en esos días: la falta de dato no se disfraza de 0
     */
    public static Optional<BigDecimal> de(LocalDate primeraFecha, List<DiaDeHabitos> dias) {
        LocalDate ultima = primeraFecha.plusDays(DIAS_MEDIDOS - 1L);
        List<Integer> porcentajes = dias.stream()
                .filter(dia -> !dia.fecha().isBefore(primeraFecha) && !dia.fecha().isAfter(ultima))
                .filter(dia -> dia.programados() > 0)
                .map(dia -> porcentajeDelDia(dia.cumplidos(), dia.programados()))
                .toList();
        if (porcentajes.isEmpty()) {
            return Optional.empty();
        }
        long suma = porcentajes.stream().mapToLong(Integer::longValue).sum();
        return Optional.of(BigDecimal.valueOf(suma)
                .divide(BigDecimal.valueOf(porcentajes.size()), 1, RoundingMode.HALF_UP));
    }

    public static boolean cumple(BigDecimal cumplimiento) {
        return cumplimiento != null && cumplimiento.compareTo(BigDecimal.valueOf(UMBRAL)) >= 0;
    }

    private static int porcentajeDelDia(int cumplidos, int programados) {
        return BigDecimal.valueOf(cumplidos).multiply(CIEN)
                .divide(BigDecimal.valueOf(programados), 0, RoundingMode.HALF_UP).intValueExact();
    }
}
