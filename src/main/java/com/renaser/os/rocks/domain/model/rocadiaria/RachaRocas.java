package com.renaser.os.rocks.domain.model.rocadiaria;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Racha más larga histórica de días con las 3 Rocas Diarias completas
 * (logro "3 Rocas en una Semana") — portado 1:1 desde el backend viejo,
 * {@code src/features/profile/service.ts::longestRocksStreakDays}
 * (líneas 248-270), consumido por {@code GET /api/v1/profile/logros}
 * (P-05, campo {@code bestRocksStreakDays}).
 *
 * <p>El algoritmo original: agrupar por fecha, quedarse solo con los días
 * que tienen 3 o más Rocas completadas ese día (un día con menos de 3 no
 * cuenta, aunque tenga alguna), ordenar esas fechas y contar la racha más
 * larga de días calendario consecutivos.
 */
public final class RachaRocas {

    private static final int ROCAS_POR_DIA_COMPLETO = 3;

    private RachaRocas() {
    }

    /**
     * @param fechasRocasCompletadas una entrada por cada Roca Diaria completada (repetidas
     *                                por fecha si hubo más de una ese día) — el mismo shape
     *                                crudo que {@code findAllCompletedRockDates} del repo viejo
     * @return 0 si nunca hubo un día con las 3 Rocas completas
     */
    public static int calcular(List<LocalDate> fechasRocasCompletadas) {
        if (fechasRocasCompletadas == null || fechasRocasCompletadas.isEmpty()) {
            return 0;
        }

        List<LocalDate> diasCompletos = diasConLasTresRocas(fechasRocasCompletadas);
        if (diasCompletos.isEmpty()) {
            return 0;
        }

        int mejorRacha = 1;
        int rachaActual = 1;
        for (int i = 1; i < diasCompletos.size(); i++) {
            boolean consecutivo = ChronoUnit.DAYS.between(diasCompletos.get(i - 1), diasCompletos.get(i)) == 1;
            rachaActual = consecutivo ? rachaActual + 1 : 1;
            mejorRacha = Math.max(mejorRacha, rachaActual);
        }
        return mejorRacha;
    }

    private static List<LocalDate> diasConLasTresRocas(List<LocalDate> fechasRocasCompletadas) {
        Map<LocalDate, Long> porFecha = fechasRocasCompletadas.stream()
                .collect(Collectors.groupingBy(fecha -> fecha, Collectors.counting()));
        return porFecha.entrySet().stream()
                .filter(entrada -> entrada.getValue() >= ROCAS_POR_DIA_COMPLETO)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }
}
