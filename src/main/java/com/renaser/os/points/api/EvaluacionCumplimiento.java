package com.renaser.os.points.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado de la fórmula. {@code porcentaje} es nullable a propósito: {@link EstadoEvaluacion}
 * dice por qué falta, y ningún consumidor debe convertirlo a cero.
 *
 * @param versionFormula identifica con qué reglas se calculó. Una corrección futura recalcula
 *                       explícitamente en vez de reescribir historia en silencio (plan.md §8).
 */
public record EvaluacionCumplimiento(BigDecimal porcentaje, int entregadas, int esperadas,
                                      int aprendicesEvaluados, int aprendicesExcluidos,
                                      int tardiasFueraDeVentana, int verificadas,
                                      EstadoEvaluacion estado, String versionFormula,
                                      List<CumplimientoAprendiz> detallePorAprendiz) {
}
