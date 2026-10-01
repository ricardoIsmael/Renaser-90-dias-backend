package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

/**
 * El reporte del cuerpo de mentores de un mes (SDD 002, RL-19..RL-23; D-241): por mentor, la atención
 * de sus consultas en el mes, su evaluación del mes y lo que el líder le dijo en el mes.
 */
public interface ConsultarReporteDeMentoresUseCase {

    /** @param mes {@code YYYY-MM}; null = el mes en curso */
    ReporteDeMentores reporte(UserId actorId, String mes);

    /**
     * @param cerrado                  el mes ya terminó: sus cifras no cambian con el paso del tiempo
     * @param respondidasSinAtribucion consultas del mes respondidas antes de V88, sin registro de quién;
     *                                 null si la fuente no respondió
     * @param ordenados                por evaluación, de mayor a menor, sin redondear (RL-23)
     * @param sinMuestra               sin evaluación calculada: fuera del orden, nombrados aparte
     */
    record ReporteDeMentores(String mes, String zona, Instant corteEn, boolean cerrado,
                             Integer respondidasSinAtribucion, List<EntradaDelReporte> ordenados,
                             List<EntradaDelReporte> sinMuestra) {
    }

    record EntradaDelReporte(IndicadoresDeMentor indicadores, Fuente<ConteoDeObservaciones> observaciones) {
    }

    record ConteoDeObservaciones(int reconocimientos, int sugerencias, int alertas) {

        public static final ConteoDeObservaciones NINGUNA = new ConteoDeObservaciones(0, 0, 0);

        public int total() {
            return reconocimientos + sugerencias + alertas;
        }
    }
}
