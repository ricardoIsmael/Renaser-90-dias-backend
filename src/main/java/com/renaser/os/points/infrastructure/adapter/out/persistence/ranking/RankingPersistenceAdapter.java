package com.renaser.os.points.infrastructure.adapter.out.persistence.ranking;

import com.renaser.os.points.application.ports.out.ranking.LoadRankingCandidatosPort;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingPort;
import com.renaser.os.points.application.ports.out.ranking.SaveRankingSnapshotPort;
import com.renaser.os.points.domain.model.ranking.PosicionRanking;
import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
class RankingPersistenceAdapter implements LoadRankingCandidatosPort, SaveRankingSnapshotPort, LoadRankingPort {

    /**
     * Solo la tabla PROPIA de `points`. Quienes son aprendices activos y como se llaman
     * lo responde el contrato publico de `users` (D-41): antes esta consulta hacia JOIN
     * contra `renaser.usuarios`, tabla ajena.
     */
    private static final String SQL_PUNTAJES = """
            SELECT p.participante_id, p.puntos_liga, p.coherencia
            FROM renaser.puntajes_participante p
            """;

    private final SpringDataRankingAprendizRepository repository;
    private final RankingPersistenceMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    private final UserSummaryFinder userSummaryFinder;

    RankingPersistenceAdapter(SpringDataRankingAprendizRepository repository, RankingPersistenceMapper mapper,
                               JdbcTemplate jdbcTemplate, UserSummaryFinder userSummaryFinder) {
        this.repository = repository;
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
        this.userSummaryFinder = userSummaryFinder;
    }

    /**
     * Dos consultas en total, no una por aprendiz: se traen todos los puntajes y se
     * resuelven los nombres/roles EN LOTE contra `users`. El filtro por rol y estado
     * queda del lado de `users`, que es su dueno.
     */
    @Override
    public List<CandidatoRanking> aprendicesActivosConPuntaje() {
        List<PuntajeCrudo> puntajes = jdbcTemplate.query(SQL_PUNTAJES, (rs, rowNum) -> new PuntajeCrudo(
                UserId.of(rs.getObject("participante_id", UUID.class)),
                rs.getInt("puntos_liga"),
                rs.getBigDecimal("coherencia")));
        if (puntajes.isEmpty()) {
            return List.of();
        }
        Map<UserId, UserSummary> resumenes = userSummaryFinder.findByIds(
                puntajes.stream().map(PuntajeCrudo::participanteId).toList());
        return puntajes.stream()
                .filter(puntaje -> compiteEnElRanking(resumenes.get(puntaje.participanteId())))
                .map(puntaje -> aCandidato(puntaje, resumenes.get(puntaje.participanteId())))
                .toList();
    }

    /**
     * Solo el aprendiz activo compite: el staff no entra al ranking, y una cuenta suspendida
     * deja de figurar. {@code null} = el puntaje quedo huerfano (el usuario ya no existe).
     */
    private static boolean compiteEnElRanking(UserSummary resumen) {
        return resumen != null && resumen.role() == UserRole.TRAINEE && resumen.status() == UserStatus.ACTIVE;
    }

    private static CandidatoRanking aCandidato(PuntajeCrudo puntaje, UserSummary resumen) {
        return new CandidatoRanking(puntaje.participanteId(), resumen.fullName(),
                puntaje.puntosLiga(), puntaje.coherencia());
    }

    private record PuntajeCrudo(UserId participanteId, int puntosLiga, java.math.BigDecimal coherencia) {
    }

    @Override
    public void reemplazar(TipoRanking tipo, LocalDate fecha, List<PosicionRanking> posiciones) {
        TipoRankingJpa tipoJpa = mapper.toJpaTipo(tipo);
        repository.deleteByTipoAndFecha(tipoJpa, fecha);
        repository.saveAllAndFlush(posiciones.stream().map(mapper::toEntity).toList());
    }

    @Override
    public List<EntradaRankingConNombre> porTipoYFecha(TipoRanking tipo, LocalDate fecha) {
        List<RankingAprendizJpaEntity> filas = repository.findByTipoAndFechaOrderByPosicion(
                mapper.toJpaTipo(tipo), fecha);

        // Una sola consulta de nombres para todo el listado: antes habia una por fila (N+1).
        Map<UserId, UserSummary> resumenes = userSummaryFinder.findByIds(
                filas.stream().map(mapper::participanteIdDe).toList());
        return filas.stream()
                .map(fila -> {
                    UserId participanteId = mapper.participanteIdDe(fila);
                    UserSummary resumen = resumenes.get(participanteId);
                    return new EntradaRankingConNombre(participanteId, resumen == null ? null : resumen.fullName(),
                            fila.getPosicion(), fila.getPuntaje());
                })
                .toList();
    }
}
