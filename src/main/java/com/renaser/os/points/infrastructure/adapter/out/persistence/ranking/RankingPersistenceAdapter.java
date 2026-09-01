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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
class RankingPersistenceAdapter implements LoadRankingCandidatosPort, SaveRankingSnapshotPort, LoadRankingPort {

    /**
     * Nombre logico del cache Caffeine (D-63, motor definido en
     * {@code shared/infrastructure/cache/CacheConfig}). El ranking se lee muchas veces por
     * cada vez que cambia — colapsa lecturas simultaneas en una sola consulta a Postgres.
     */
    private static final String CACHE_RANKING = "ranking";

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

    /**
     * Invalida la entrada de cache de ESTE tipo+fecha al toque: el snapshot nocturno
     * (D-63, {@code SnapshotRankingScheduler}) no debe esperar el TTL para reflejarse — mismo
     * criterio que la invalidacion por evento de CLAUDE.MD sec. 5.3.5 (el TTL es la red de
     * seguridad, no el mecanismo principal).
     */
    @Override
    @CacheEvict(cacheNames = CACHE_RANKING, key = "#tipo + '|' + #fecha")
    public void reemplazar(TipoRanking tipo, LocalDate fecha, List<PosicionRanking> posiciones) {
        TipoRankingJpa tipoJpa = mapper.toJpaTipo(tipo);
        repository.deleteByTipoAndFecha(tipoJpa, fecha);
        repository.saveAllAndFlush(posiciones.stream().map(mapper::toEntity).toList());
    }

    /**
     * Cacheado en memoria (D-63): es el hot path que muchas personas miran a la vez y que
     * cambia poco (1 snapshot por dia). La clave INCLUYE tipo y fecha — los dos parametros
     * que cambian el resultado — para no servirle a alguien el ranking de otra consulta.
     * Nunca el actor: esta consulta no varia segun quien pregunta (la posicion propia no se
     * marca aca, ver {@code RankingAgregadoService}), asi que el actor NO forma parte de la
     * clave a proposito.
     */
    @Override
    @Cacheable(cacheNames = CACHE_RANKING, key = "#tipo + '|' + #fecha")
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
