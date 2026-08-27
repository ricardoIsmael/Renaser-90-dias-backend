package com.renaser.os.points.infrastructure.adapter.in.rest.ranking;

import com.renaser.os.points.application.ports.in.ranking.ConsultarRankingAgregadoUseCase.RankingAgregado;
import com.renaser.os.points.application.ports.in.ranking.ConsultarRankingAgregadoUseCase.RankingAgregado.CelulaResumen;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/v1/ranking}. {@code celula} es {@code null} si el actor todavia no tiene
 * una celula asignada — estado normal del proceso, no un error (mismo criterio que
 * {@code community.MiCelulaResponse}).
 */
public record RankingAgregadoResponse(LocalDate fecha, CelulaResumenResponse celula,
                                       List<EntradaRankingResponse> liga,
                                       List<EntradaRankingResponse> coherenciaIndividual,
                                       List<EntradaRankingResponse> general) {

    public static RankingAgregadoResponse from(RankingAgregado agregado) {
        return new RankingAgregadoResponse(agregado.fecha(), CelulaResumenResponse.from(agregado.celula()),
                agregado.liga().stream().map(EntradaRankingResponse::from).toList(),
                agregado.coherenciaIndividual().stream().map(EntradaRankingResponse::from).toList(),
                agregado.general().stream().map(EntradaRankingResponse::from).toList());
    }

    public record CelulaResumenResponse(String cellId, String cellName, String cohortName, String mentorName,
                                         int memberCount, int totalCellsInCohort) {

        static CelulaResumenResponse from(CelulaResumen celula) {
            if (celula == null) {
                return null;
            }
            return new CelulaResumenResponse(celula.celulaId().toString(), celula.cellName(), celula.cohortName(),
                    celula.mentorName(), celula.memberCount(), celula.totalCellsInCohort());
        }
    }
}
