package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarReporteDeMentoresUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarReporteDeMentoresUseCase.EntradaDelReporte;
import com.renaser.os.leadership.application.ports.in.ConsultarReporteDeMentoresUseCase.ReporteDeMentores;
import com.renaser.os.leadership.domain.model.documento.DatosDelMes;
import com.renaser.os.leadership.domain.model.documento.DatosDelMes.MentorDelRanking;
import com.renaser.os.leadership.domain.model.mentor.PeorEvaluacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

/**
 * Reúne los datos del reporte del mes (spec 056, FR-032 y FR-031) del mismo reporte que ve el líder en la app ({@link ConsultarReporteDeMentoresUseCase}):
 * el promedio de los mentores, cuántos están debajo del 60 % —el umbral de {@link PeorEvaluacion}— y el ranking con sus consultas respondidas.
 * Quien pide el reporte ya quedó autorizado por ese caso de uso, y ve solo los mentores de sus formaciones.
 */
@Component
class ArmadoDelDocumentoDelMes {

    private static final int UN_DECIMAL = 1;

    private final ConsultarReporteDeMentoresUseCase reporte;

    ArmadoDelDocumentoDelMes(ConsultarReporteDeMentoresUseCase reporte) {
        this.reporte = reporte;
    }

    /** @param mes {@code AAAA-MM}; el reporte rechaza uno mal escrito o que todavía no empezó */
    DatosDelMes armar(UserId actor, String mes) {
        ReporteDeMentores delMes = reporte.reporte(actor, mes);
        YearMonth periodo = YearMonth.parse(delMes.mes());
        List<MentorDelRanking> ranking = delMes.ordenados().stream().map(ArmadoDelDocumentoDelMes::mentor).toList();
        return new DatosDelMes(delMes.mes(), periodo.atDay(1), periodo.atEndOfMonth(), delMes.zona(), promedio(ranking),
                (int) ranking.stream().filter(m -> m.evaluacion().compareTo(PeorEvaluacion.UMBRAL) < 0).count(), ranking,
                delMes.sinMuestra().stream().map(entrada -> entrada.indicadores().nombre()).toList());
    }

    private static MentorDelRanking mentor(EntradaDelReporte entrada) {
        var indicadores = entrada.indicadores();
        Integer respondidas = indicadores.atencion().disponible() ? indicadores.atencion().valor().respondidas() : null;
        Integer observaciones = entrada.observaciones().disponible() ? entrada.observaciones().valor().total() : null;
        return new MentorDelRanking(indicadores.nombre(), indicadores.evaluacion().valor().porcentaje(), respondidas, observaciones);
    }

    private static BigDecimal promedio(List<MentorDelRanking> ranking) {
        if (ranking.isEmpty()) {
            return null;
        }
        return ranking.stream().map(MentorDelRanking::evaluacion).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(ranking.size()), UN_DECIMAL, RoundingMode.HALF_UP);
    }
}
