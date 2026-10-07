package com.renaser.os.points.application.services;

import com.renaser.os.points.api.PorcentajeCursosFinder;
import com.renaser.os.points.api.PorcentajeHabitosFinder;
import com.renaser.os.points.api.RankingGeneralFinder;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingCandidatosPort;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingCandidatosPort.CandidatoRanking;
import com.renaser.os.points.domain.model.ranking.PuntajeGeneral;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentasCerradasFinder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * El ranking general a un corte, sin guardar foto (D-262). Los mismos candidatos que la foto diaria
 * ({@code LoadRankingCandidatosPort}), la misma fórmula ({@link PuntajeGeneral}), las mismas dos consultas en
 * lote (D-43) y el mismo filtro de cuentas cerradas que aplica la pestaña al leer ({@code RankingService.consultar}).
 *
 * <p>Clase aparte y no un método más de {@code RankingService}: ese servicio genera y guarda la foto diaria;
 * esto solo calcula, para el podio semanal del chat, con {@code hasta} = el domingo que cerró la semana.
 */
@Service
public class RankingGeneralAlCorteService implements RankingGeneralFinder {

    private static final Comparator<PuestoEnElRankingGeneral> DE_MAYOR_A_MENOR =
            Comparator.comparing(PuestoEnElRankingGeneral::puntaje).reversed()
                    .thenComparing(p -> p.participanteId().value());

    private final LoadRankingCandidatosPort candidatos;
    private final PorcentajeHabitosFinder habitos;
    private final PorcentajeCursosFinder cursos;
    private final CuentasCerradasFinder cuentasCerradas;

    RankingGeneralAlCorteService(LoadRankingCandidatosPort candidatos, PorcentajeHabitosFinder habitos,
                                 PorcentajeCursosFinder cursos, CuentasCerradasFinder cuentasCerradas) {
        this.candidatos = candidatos;
        this.habitos = habitos;
        this.cursos = cursos;
        this.cuentasCerradas = cuentasCerradas;
    }

    @Override
    public List<PuestoEnElRankingGeneral> alCorte(LocalDate hasta) {
        List<CandidatoRanking> padron = candidatos.aprendicesActivosConPuntaje();
        if (padron.isEmpty()) {
            return List.of();
        }
        List<UserId> ids = padron.stream().map(CandidatoRanking::participanteId).toList();
        Set<UserId> cerradas = cuentasCerradas.cerradasEntre(ids);
        Map<UserId, BigDecimal> porcentajeHabitos = habitos.porcentajePorParticipante(ids, hasta);
        Map<UserId, BigDecimal> porcentajeCursos = cursos.porcentajePorParticipante(ids);
        return padron.stream()
                .filter(c -> !cerradas.contains(c.participanteId()))
                .map(c -> new PuestoEnElRankingGeneral(c.participanteId(), c.fullName(),
                        puntaje(porcentajeHabitos.get(c.participanteId()), porcentajeCursos.get(c.participanteId()))))
                .sorted(DE_MAYOR_A_MENOR)
                .toList();
    }

    /** Sin dato en ningún módulo va con cero, como en la pestaña ({@code RankingService.puntajeDe}, D-131). */
    private static BigDecimal puntaje(BigDecimal habitos, BigDecimal cursos) {
        return PuntajeGeneral.calcular(habitos, cursos).orElse(BigDecimal.ZERO);
    }
}
