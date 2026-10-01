package com.renaser.os.mentoring.api;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;

/**
 * La evaluación mensual de un mentor, leída por otro (la gestión del Líder de Mentores, SDD 002 RL-11;
 * D-241). Es la MISMA que el mentor ve de sí mismo en {@code GET /api/v1/mentor/me/evaluation}: el motor
 * único de {@code points}, sobre sus tramos reales de acompañamiento. No hay una segunda fórmula.
 *
 * <p>Agregada y sin nombres de aprendices.
 */
public interface EvaluacionDeMentorFinder {

    EvaluacionDeMentor evaluacionDe(UserId mentorId, YearMonth mes);

    /**
     * @param porcentaje null cuando {@code estado} no es CALCULADA. Nunca cero por falta de datos.
     * @param estado     CALCULADA, SIN_MUESTRA o SIN_HISTORIAL ({@code points.api.EstadoEvaluacion})
     */
    record EvaluacionDeMentor(String mes, String zona, BigDecimal porcentaje, int entregadas, int esperadas,
                              int alumnosEvaluados, String estado, String versionFormula, Instant corteEn) {
    }
}
