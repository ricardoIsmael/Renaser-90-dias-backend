package com.renaser.os.mentoring.application.ports.in;

import com.renaser.os.mentoring.domain.model.quesigue.QueSigue.Accion;
import com.renaser.os.mentoring.domain.model.quesigue.QueSigue.AprendizQueBaja;
import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * La evaluación mensual propia del mentor.
 *
 * <p>Agregada: el mentor saliente conserva su resumen, no el expediente de los alumnos que ya no acompaña (P-07). Los nombres solo
 * salen en {@code quienBaja} y {@code acciones}, y solo de quienes acompaña HOY (spec 057).
 */
public interface ConsultarEvaluacionPropiaUseCase {

    EvaluacionPropia evaluacionDe(UserId actorId, YearMonth mes);

    /**
     * @param porcentaje   null cuando {@code estado} no es CALCULADA. Nunca cero por falta de datos.
     * @param tramos       intervalos reales en los que acompañó durante el mes.
     * @param versionFormula con qué reglas se calculó, para poder recalcular a propósito después.
     * @param porcentajeMesAnterior (spec 057, FR-010) el del mes anterior; null si ese mes no está CALCULADA.
     * @param quienBaja    (FR-012) hasta 5 aprendices que acompaña HOY con menos de 80 % en el mes, del menor al mayor; vacío sin cálculo.
     * @param acciones     (FR-011) «Qué sigue»: hasta 3 acciones con su motivo; vacío sin cálculo.
     */
    record EvaluacionPropia(String mes, String zona, BigDecimal porcentaje, int entregadas, int esperadas,
                             int alumnosEvaluados, int alumnosExcluidos, int tardiasFueraDeVentana,
                             int verificadas, String estado, String versionFormula, Instant corteEn,
                             List<TramoEvaluado> tramos, BigDecimal porcentajeMesAnterior,
                             List<AprendizQueBaja> quienBaja, List<Accion> acciones) {

        /** La respuesta de antes de la spec 057: sin mes anterior, sin «Quién baja» y sin «Qué sigue». */
        public EvaluacionPropia(String mes, String zona, BigDecimal porcentaje, int entregadas, int esperadas,
                                int alumnosEvaluados, int alumnosExcluidos, int tardiasFueraDeVentana,
                                int verificadas, String estado, String versionFormula, Instant corteEn,
                                List<TramoEvaluado> tramos) {
            this(mes, zona, porcentaje, entregadas, esperadas, alumnosEvaluados, alumnosExcluidos, tardiasFueraDeVentana,
                    verificadas, estado, versionFormula, corteEn, tramos, null, List.of(), List.of());
        }
    }

    record TramoEvaluado(String grupoNombre, LocalDate desde, LocalDate hasta) {
    }
}
