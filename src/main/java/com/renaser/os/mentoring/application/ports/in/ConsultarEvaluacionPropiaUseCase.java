package com.renaser.os.mentoring.application.ports.in;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * La evaluación mensual propia del mentor.
 *
 * <p>Agregada y sin nombres: el mentor saliente conserva su resumen, no el expediente de los
 * alumnos que ya no acompaña (P-07).
 */
public interface ConsultarEvaluacionPropiaUseCase {

    EvaluacionPropia evaluacionDe(UserId actorId, YearMonth mes);

    /**
     * @param porcentaje   null cuando {@code estado} no es CALCULADA. Nunca cero por falta de datos.
     * @param tramos       intervalos reales en los que acompañó durante el mes.
     * @param versionFormula con qué reglas se calculó, para poder recalcular a propósito después.
     */
    record EvaluacionPropia(String mes, String zona, BigDecimal porcentaje, int entregadas, int esperadas,
                             int alumnosEvaluados, int alumnosExcluidos, int tardiasFueraDeVentana,
                             int verificadas, String estado, String versionFormula, Instant corteEn,
                             List<TramoEvaluado> tramos) {
    }

    record TramoEvaluado(String grupoNombre, LocalDate desde, LocalDate hasta) {
    }
}
