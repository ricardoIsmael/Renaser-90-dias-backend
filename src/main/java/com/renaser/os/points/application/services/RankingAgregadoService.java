package com.renaser.os.points.application.services;

import com.renaser.os.community.api.CelulaFinder;
import com.renaser.os.community.api.CelulaFinder.CelulaParticipanteResumen;
import com.renaser.os.points.application.ports.in.ranking.ConsultarRankingAgregadoUseCase;
import com.renaser.os.points.application.ports.in.ranking.ConsultarRankingUseCase;
import com.renaser.os.points.application.ports.in.ranking.ConsultarRankingUseCase.EntradaRanking;
import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Implementa {@link ConsultarRankingAgregadoUseCase} reutilizando
 * {@link ConsultarRankingUseCase#consultar} tal cual — misma validacion de actor activo que
 * cada pestaña plana, sin duplicarla aca. La celula del actor se resuelve aparte via
 * {@code community.api.CelulaFinder} (D-41).
 */
@Service
public class RankingAgregadoService implements ConsultarRankingAgregadoUseCase {

    private final ConsultarRankingUseCase consultarRankingUseCase;
    private final CelulaFinder celulaFinder;

    public RankingAgregadoService(ConsultarRankingUseCase consultarRankingUseCase, CelulaFinder celulaFinder) {
        this.consultarRankingUseCase = consultarRankingUseCase;
        this.celulaFinder = celulaFinder;
    }

    @Override
    public RankingAgregado agregado(UserId actorId, LocalDate fecha) {
        List<EntradaRanking> liga = consultarRankingUseCase.consultar(actorId, TipoRanking.LEAGUE, fecha);
        List<EntradaRanking> coherenciaIndividual = consultarRankingUseCase.consultar(actorId, TipoRanking.CELL,
                fecha);
        List<EntradaRanking> general = consultarRankingUseCase.consultar(actorId, TipoRanking.GENERAL, fecha);
        RankingAgregado.CelulaResumen celula = celulaFinder.celulaDeParticipante(actorId)
                .map(RankingAgregadoService::aCelulaResumen)
                .orElse(null);
        return new RankingAgregado(fecha, celula, liga, coherenciaIndividual, general);
    }

    private static RankingAgregado.CelulaResumen aCelulaResumen(CelulaParticipanteResumen resumen) {
        return new RankingAgregado.CelulaResumen(resumen.celulaId(), resumen.cellName(), resumen.cohortName(),
                resumen.mentorName(), resumen.memberCount(), resumen.totalCellsInCohort());
    }
}
