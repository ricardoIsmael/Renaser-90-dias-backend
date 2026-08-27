package com.renaser.os.points.application.ports.in.ranking;

import com.renaser.os.points.application.ports.in.ranking.ConsultarRankingUseCase.EntradaRanking;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/v1/ranking} — agregador de un solo llamado (gap #24,
 * docs/PLAN_INTEGRACION_FRONTEND.md §3/§5, decision ya tomada por el dueno del producto:
 * "se construye el agregador"). Compone los 3 rankings que {@code points} ya genera
 * (LEAGUE, CELL, GENERAL — COHORT sigue sin ser generable, D-P7 de docs/MODULO_POINTS.md)
 * mas la celula del actor, leida de {@code community.api.CelulaFinder} (D-41: {@code points}
 * nunca toca la tabla {@code celulas} de frente).
 *
 * <p><b>Lo que este caso de uso NO hace, a proposito:</b> no replica el contrato viejo
 * {@code {cohortName, celulas, miCelula, miCelulaPorHabitos, miCelulaGeneral}} del backend
 * Next.js (rankings de miembros DENTRO de la celula propia, mas ranking de celulas dentro
 * del cohort ordenadas por {@code coherenceScoreGroup}/{@code rankingPosition}). Esa forma
 * exige decidir primero quien genera y con que formula la tabla {@code ranking_celulas}
 * (docs/MODULO_POINTS.md Q-1/Q-1b: hoy sin persistencia en ningun modulo) — pregunta abierta
 * de arquitectura que este cambio no resuelve por su cuenta (CLAUDE.MD sec. 0.6, "no
 * inventar reglas de negocio"). Lo que si se puede componer hoy sin inventar nada son los 3
 * rankings existentes (ya generados por {@link GenerarSnapshotRankingUseCase}) mas el dato
 * de celula del actor.
 */
public interface ConsultarRankingAgregadoUseCase {

    RankingAgregado agregado(UserId actorId, LocalDate fecha);

    /**
     * @param celula                celula del actor, {@code null} si todavia no tiene una asignada
     * @param liga                  snapshot {@link com.renaser.os.points.domain.model.ranking.TipoRanking#LEAGUE}
     * @param coherenciaIndividual  snapshot {@link com.renaser.os.points.domain.model.ranking.TipoRanking#CELL} —
     *                              pese al nombre del tipo, hoy ordena a TODOS los aprendices activos por
     *                              coherencia individual, no esta scoped a una celula (ver D-P7/Q-1)
     * @param general               snapshot {@link com.renaser.os.points.domain.model.ranking.TipoRanking#GENERAL}
     */
    record RankingAgregado(LocalDate fecha, CelulaResumen celula, List<EntradaRanking> liga,
                            List<EntradaRanking> coherenciaIndividual, List<EntradaRanking> general) {

        /** Proyeccion publica de la celula del actor, tal como la expone {@code community.api.CelulaFinder}. */
        public record CelulaResumen(UUID celulaId, String cellName, String cohortName, String mentorName,
                                     int memberCount, int totalCellsInCohort) {
        }
    }
}
