package com.renaser.os.rocks.domain.model.coherencia;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Ley VI para Rocas Diarias — MISMO criterio, literal, que hábitos usa para
 * su propio % de coherencia (repo viejo: {@code src/lib/coherence.ts::
 * averageCompletionForDates}; producción vigente: la función SQL
 * {@code general_ranking_scores()}, ver {@code prisma/migrations/
 * general_ranking_scores_function.sql}, sección {@code rocas_pct}). Ambas
 * fuentes coinciden — no hubo que elegir una sobre la otra.
 *
 * <p><b>La fórmula, con doble redondeo deliberado (no es un bug, el comentario
 * del repo viejo lo dice explícito):</b>
 * <ol>
 *   <li>Ventana de 7 días UTC cerrados, terminando (incluido) en la fecha
 *       pedida ({@code hasta}) — la resuelve {@code PorcentajeRocasService},
 *       esta clase solo recibe los días ya filtrados.</li>
 *   <li>Cada día se redondea a entero PRIMERO ({@link DiaRocas#puntajeDelDia()}).</li>
 *   <li>LUEGO se promedian esos enteros, con 1 decimal de precisión final —
 *       de ahí {@link BigDecimal}, no {@code Integer}: truncar a entero acá
 *       perdería la precisión que {@code points} necesita para su propio
 *       redondeo final a 1 decimal (D-43, 50% hábitos + 35% rocas + 15%
 *       cursos).</li>
 *   <li>Ventana sin ningún día calificable (participante recién empezó, o
 *       sin Rocas Diarias planificadas en los últimos 7 días) → 100. No se lo
 *       castiga por no tener datos todavía.</li>
 * </ol>
 *
 * <p>Días con {@code total == 0} nunca llegan hasta acá — {@link DiaRocas}
 * los rechaza en su constructor; la consulta en lote simplemente no genera
 * una fila para un día sin ninguna Roca Diaria.
 */
public final class PorcentajeRocas {

    /** Escala 1, igual que todo porcentaje del contrato: `100` y `100.0` no son iguales para BigDecimal. */
    private static final BigDecimal CIEN = new BigDecimal("100.0");
    private static final int ESCALA_INTERMEDIA = 10;

    private PorcentajeRocas() {
    }

    public static BigDecimal calcular(List<DiaRocas> dias) {
        if (dias == null || dias.isEmpty()) {
            return CIEN;
        }
        BigDecimal suma = dias.stream()
                .map(dia -> BigDecimal.valueOf(dia.puntajeDelDia()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal promedio = suma.divide(BigDecimal.valueOf(dias.size()), ESCALA_INTERMEDIA, RoundingMode.HALF_UP);
        return promedio.setScale(1, RoundingMode.HALF_UP);
    }
}
