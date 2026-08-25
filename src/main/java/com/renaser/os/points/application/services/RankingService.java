package com.renaser.os.points.application.services;

import com.renaser.os.points.api.PorcentajeCursosFinder;
import com.renaser.os.points.api.PorcentajeHabitosFinder;
import com.renaser.os.points.application.ports.in.ranking.ConsultarRankingUseCase;
import com.renaser.os.points.application.ports.in.ranking.GenerarSnapshotRankingUseCase;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingCandidatosPort.CandidatoRanking;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingCandidatosPort;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingPort;
import com.renaser.os.points.application.ports.out.ranking.SaveRankingSnapshotPort;
import com.renaser.os.points.domain.model.ranking.PosicionRanking;
import com.renaser.os.points.domain.model.ranking.PuntajeGeneral;
import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.points.api.PorcentajeRocasFinder;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummaryFinder;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RankingService implements ConsultarRankingUseCase, GenerarSnapshotRankingUseCase {

    private final LoadRankingCandidatosPort loadRankingCandidatosPort;
    private final SaveRankingSnapshotPort saveRankingSnapshotPort;
    private final LoadRankingPort loadRankingPort;
    private final PorcentajeHabitosFinder porcentajeHabitosFinder;
    private final PorcentajeRocasFinder porcentajeRocasFinder;
    private final PorcentajeCursosFinder porcentajeCursosFinder;
    private final UserSummaryFinder userSummaryFinder;

    public RankingService(LoadRankingCandidatosPort loadRankingCandidatosPort,
                           SaveRankingSnapshotPort saveRankingSnapshotPort, LoadRankingPort loadRankingPort,
                           PorcentajeHabitosFinder porcentajeHabitosFinder,
                           PorcentajeRocasFinder porcentajeRocasFinder,
                           PorcentajeCursosFinder porcentajeCursosFinder, UserSummaryFinder userSummaryFinder) {
        this.loadRankingCandidatosPort = loadRankingCandidatosPort;
        this.saveRankingSnapshotPort = saveRankingSnapshotPort;
        this.loadRankingPort = loadRankingPort;
        this.porcentajeHabitosFinder = porcentajeHabitosFinder;
        this.porcentajeRocasFinder = porcentajeRocasFinder;
        this.porcentajeCursosFinder = porcentajeCursosFinder;
        this.userSummaryFinder = userSummaryFinder;
    }

    @Override
    @Transactional
    public void generar(TipoRanking tipo, LocalDate fecha) {
        requireGenerable(tipo);

        List<CandidatoRanking> candidatos = loadRankingCandidatosPort.aprendicesActivosConPuntaje();
        Map<UserId, BigDecimal> puntajesGenerales = tipo == TipoRanking.GENERAL
                ? puntajesGeneralesDe(candidatos, fecha)
                : Map.of();
        List<PosicionRanking> posiciones = ordenarYNumerar(tipo, fecha, candidatos, puntajesGenerales);

        saveRankingSnapshotPort.reemplazar(tipo, fecha, posiciones);
    }

    /**
     * Tres consultas EN LOTE, una por modulo — nunca una por aprendiz (D-43). El backend
     * viejo hacia justamente eso para el progreso de cursos y con ~30 cuentas activas
     * agotaba las conexiones; por eso habia terminado en un procedimiento almacenado. Aca
     * la formula vive en el dominio ({@link PuntajeGeneral}) y lo unico que se resuelve en
     * lote es el dato crudo de cada modulo.
     */
    private Map<UserId, BigDecimal> puntajesGeneralesDe(List<CandidatoRanking> candidatos, LocalDate fecha) {
        List<UserId> participantes = candidatos.stream().map(CandidatoRanking::participanteId).toList();
        if (participantes.isEmpty()) {
            return Map.of();
        }
        Map<UserId, BigDecimal> habitos = porcentajeHabitosFinder.porcentajePorParticipante(participantes, fecha);
        Map<UserId, BigDecimal> rocas = porcentajeRocasFinder.porcentajePorParticipante(participantes, fecha);
        Map<UserId, BigDecimal> cursos = porcentajeCursosFinder.porcentajePorParticipante(participantes);

        Map<UserId, BigDecimal> puntajes = new LinkedHashMap<>(participantes.size());
        for (UserId participante : participantes) {
            puntajes.put(participante, PuntajeGeneral.calcular(habitos.get(participante),
                    rocas.get(participante), cursos.get(participante)));
        }
        return puntajes;
    }

    @Override
    public List<EntradaRanking> consultar(UserId actorId, TipoRanking tipo, LocalDate fecha) {
        requireActorActivo(actorId);
        return loadRankingPort.porTipoYFecha(tipo, fecha).stream()
                .map(e -> new EntradaRanking(e.participanteId(), e.fullName(), e.posicion(), e.puntaje()))
                .toList();
    }

    /** El ranking muestra a TODOS los aprendices: exige actor real y activo, no solo existente. */
    private void requireActorActivo(UserId actorId) {
        boolean activo = userSummaryFinder.findById(actorId)
                .map(resumen -> resumen.status() == UserStatus.ACTIVE)
                .orElse(false);
        if (!activo) {
            throw new NotAuthorizedException("Se requiere una cuenta activa para consultar el ranking");
        }
    }

    private List<PosicionRanking> ordenarYNumerar(TipoRanking tipo, LocalDate fecha,
                                                    List<CandidatoRanking> candidatos,
                                                    Map<UserId, BigDecimal> puntajesGenerales) {
        List<CandidatoRanking> ordenados = candidatos.stream()
                .sorted(Comparator.comparing((CandidatoRanking c) -> puntajeDe(tipo, c, puntajesGenerales))
                        .reversed()
                        // desempate estable: mismo score no debe reordenar aleatoriamente entre corridas.
                        .thenComparing(c -> c.participanteId().value()))
                .toList();

        List<PosicionRanking> posiciones = new ArrayList<>(ordenados.size());
        for (int i = 0; i < ordenados.size(); i++) {
            CandidatoRanking candidato = ordenados.get(i);
            posiciones.add(new PosicionRanking(fecha, tipo, candidato.participanteId(), i + 1,
                    puntajeDe(tipo, candidato, puntajesGenerales)));
        }
        return posiciones;
    }

    private BigDecimal puntajeDe(TipoRanking tipo, CandidatoRanking candidato,
                                  Map<UserId, BigDecimal> puntajesGenerales) {
        return switch (tipo) {
            case LEAGUE -> BigDecimal.valueOf(candidato.puntosLiga());
            case CELL -> candidato.coherencia();
            case GENERAL -> puntajesGenerales.getOrDefault(candidato.participanteId(), PuntajeGeneral.SIN_DATO);
            case COHORT -> throw new UnsupportedOperationException(unsupportedMessage(tipo));
        };
    }

    private void requireGenerable(TipoRanking tipo) {
        if (tipo == TipoRanking.COHORT) {
            throw new UnsupportedOperationException(unsupportedMessage(tipo));
        }
    }

    private String unsupportedMessage(TipoRanking tipo) {
        return "Snapshot tipo " + tipo + " requiere agrupar por cohorte, dato que todavia no expone "
                + "ningun contrato publico — ver docs/MODULO_POINTS.md";
    }
}
