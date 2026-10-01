package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarReporteDeMentoresUseCase.ConteoDeObservaciones;
import com.renaser.os.leadership.application.ports.in.ConsultarReporteDeMentoresUseCase.EntradaDelReporte;
import com.renaser.os.leadership.application.ports.in.ConsultarReporteDeMentoresUseCase.ReporteDeMentores;
import com.renaser.os.leadership.application.ports.in.Fuente;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/v1/leadership/report?month=YYYY-MM}: el reporte del mes (RL-19..RL-23).
 *
 * @param answeredWithoutAttribution respuestas del mes sin registro de quién (anteriores a V88); null si
 *                                   la fuente no respondió
 * @param ranked                     por evaluación, de mayor a menor (valor sin redondear)
 * @param withoutSample              sin evaluación calculada: fuera del orden, nombrados aparte
 */
public record MentorReportResponse(String month, String timezone, Instant cutoffAt, boolean closed,
                                   Integer answeredWithoutAttribution, List<EntryResponse> ranked,
                                   List<EntryResponse> withoutSample) {

    static MentorReportResponse from(ReporteDeMentores reporte) {
        boolean enCurso = !reporte.cerrado();
        return new MentorReportResponse(reporte.mes(), reporte.zona(), reporte.corteEn(), reporte.cerrado(),
                reporte.respondidasSinAtribucion(),
                reporte.ordenados().stream().map(e -> EntryResponse.from(e, enCurso)).toList(),
                reporte.sinMuestra().stream().map(e -> EntryResponse.from(e, enCurso)).toList());
    }

    /** Los pendientes son de HOY: en un mes cerrado no se muestran como si fueran de ese mes. */
    public record EntryResponse(MentorIndicatorsResponse mentor, ObservationsBlock observations) {
        static EntryResponse from(EntradaDelReporte entrada, boolean conPendientes) {
            return new EntryResponse(MentorIndicatorsResponse.from(entrada.indicadores(), conPendientes),
                    ObservationsBlock.from(entrada.observaciones()));
        }
    }

    public record ObservationsBlock(String source, Integer recognitions, Integer suggestions, Integer alerts,
                                    Integer total) {
        static ObservationsBlock from(Fuente<ConteoDeObservaciones> conteo) {
            if (!conteo.disponible()) {
                return new ObservationsBlock(MentorIndicatorsResponse.UNAVAILABLE, null, null, null, null);
            }
            ConteoDeObservaciones c = conteo.valor();
            return new ObservationsBlock(MentorIndicatorsResponse.OK, c.reconocimientos(), c.sugerencias(), c.alertas(),
                    c.total());
        }
    }
}
