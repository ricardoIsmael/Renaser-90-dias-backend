package com.renaser.os.points.infrastructure.adapter.out.persistence.ranking;

import com.renaser.os.points.application.ports.out.ranking.LoadRankingCandidatosPort;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingPort;
import com.renaser.os.points.application.ports.out.ranking.SaveRankingSnapshotPort;
import com.renaser.os.points.domain.model.ranking.PosicionRanking;
import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.infrastructure.persistence.FiltroDeFormacionSql;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

@Component
class RankingPersistenceAdapter implements LoadRankingCandidatosPort, SaveRankingSnapshotPort, LoadRankingPort {

    private static final Logger log = LoggerFactory.getLogger(RankingPersistenceAdapter.class);

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
    /** E-450: el padron del ranking se cruza con quienes tienen fila en el programa. */
    private final ParticipacionProgramaFinder participacionProgramaFinder;
    RankingPersistenceAdapter(SpringDataRankingAprendizRepository repository, RankingPersistenceMapper mapper,
                               JdbcTemplate jdbcTemplate, UserSummaryFinder userSummaryFinder,
                               ParticipacionProgramaFinder participacionProgramaFinder) {
        this.repository = repository;
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
        this.userSummaryFinder = userSummaryFinder;
        this.participacionProgramaFinder = participacionProgramaFinder;
    }

    /**
     * <b>Compite TODO el padron de aprendices activos</b>, haya sumado puntos o no (D-130).
     *
     * <blockquote><b>Corregido el 2026-09-15.</b> Antes esto arrancaba de
     * {@code puntajes_participante} y se quedaba con los que ademas fueran aprendices activos. El
     * problema es cuando se crea esa fila: la primera vez que alguien SUMA puntos. Un aprendiz
     * recien incorporado no tenia fila, asi que <b>no existia para el ranking</b> — y una cohorte
     * entera que todavia no hizo nada daba una tabla vacia. Comprobado en la base local del dueño:
     * 25 aprendices activos, 1 sola fila de puntaje, y esa era de una cuenta ADMIN que ni siquiera
     * compite. El corte salia con cero filas.</blockquote>
     *
     * <p>Nadie tiene que "activarse" para aparecer: se entra al ranking por estar en el programa.
     * Quien no tiene fila de puntaje entra con <b>cero</b>, que es lo que efectivamente lleva
     * hecho.
     *
     * <p>Dos consultas en total, no una por aprendiz: el padron sale EN LOTE de {@code users}
     * —dueño del rol y del estado, que por eso no se filtran aca— y los puntajes de una sola
     * consulta.
     *
     * <blockquote><b>Corregido el 2026-09-29 (E-450).</b> El padron era {@code aprendicesActivos()} a
     * secas: rol APRENDIZ y cuenta ACTIVA. Pero {@code ranking_aprendices.participante_id} referencia a
     * {@code participantes_programa}, y una cuenta con rol aprendiz puede no tener esa fila (una
     * invitacion con rol aprendiz, E-367). Bastaba UNA para que el INSERT del corte violara la FK y
     * los cuatro rankings de TODOS quedaran sin corte. Ahora compite solo quien esta en el programa;
     * el resto se excluye con un WARN que dice cuantos y por que.</blockquote>
     */
    @Override
    public List<CandidatoRanking> aprendicesActivosConPuntaje() {
        List<UserSummary> padron = enElPrograma(userSummaryFinder.aprendicesActivos());
        if (padron.isEmpty()) {
            return List.of();
        }
        Map<UserId, PuntajeCrudo> puntajes = jdbcTemplate.query(SQL_PUNTAJES, (rs, rowNum) -> new PuntajeCrudo(
                        UserId.of(rs.getObject("participante_id", UUID.class)),
                        rs.getInt("puntos_liga"),
                        rs.getBigDecimal("coherencia")))
                .stream()
                .collect(Collectors.toMap(PuntajeCrudo::participanteId, p -> p));
        Map<UserId, Formacion> formacionPorAprendiz = formacionesDe(padron);
        return padron.stream()
                .map(aprendiz -> aCandidato(puntajes.get(aprendiz.id()), aprendiz,
                        formacionPorAprendiz.getOrDefault(aprendiz.id(), Formacion.POR_DEFECTO)))
                .toList();
    }

    /**
     * La formación de cada aprendiz del padrón (spec 024: cada ranking compara solo a los suyos), en UNA consulta para todos.
     *
     * <p>Auditoría de consultas del 2026-11-02 (H-02): antes eran tres consultas ({@code dentroDelAlcance} una vez por formación) con los ~1.000 ids
     * del padrón en cada una. El padrón ya salió de {@code participantes_programa} (solo compite quien tiene fila: {@link #enElPrograma}), y la formación
     * de un aprendiz es la de su participación, así que se lee directo de ahí.
     */
    private Map<UserId, Formacion> formacionesDe(List<UserSummary> padron) {
        Map<UserId, Formacion> resultado = new HashMap<>();
        participacionProgramaFinder.deParticipantes(padron.stream().map(UserSummary::id).toList())
                .forEach((id, participacion) -> resultado.put(id, participacion.formacion()));
        return resultado;
    }

    /**
     * Una consulta mas, en lote (D-43): los inscritos activos salen de {@code users.api}, no de un
     * SQL propio contra {@code participantes_programa} (D-41).
     */
    private List<UserSummary> enElPrograma(List<UserSummary> aprendices) {
        if (aprendices.isEmpty()) {
            return aprendices;
        }
        Set<UserId> inscritos = Set.copyOf(participacionProgramaFinder.participantesInscritosActivos());
        List<UserSummary> enElPrograma = aprendices.stream().filter(a -> inscritos.contains(a.id())).toList();
        int excluidos = aprendices.size() - enElPrograma.size();
        if (excluidos > 0) {
            log.warn("[points.ranking] {} aprendiz(es) activo(s) excluido(s) del ranking: no tienen fila en "
                    + "participantes_programa (cuenta con rol aprendiz sin programa, E-367/E-450)", excluidos);
        }
        return enElPrograma;
    }

    /** {@code puntaje} nulo = todavia no sumo nada: entra con cero, no se lo deja afuera (D-130). */
    private static CandidatoRanking aCandidato(PuntajeCrudo puntaje, UserSummary resumen, Formacion formacion) {
        if (puntaje == null) {
            return new CandidatoRanking(resumen.id(), resumen.fullName(), 0, java.math.BigDecimal.ZERO, formacion);
        }
        return new CandidatoRanking(resumen.id(), resumen.fullName(), puntaje.puntosLiga(), puntaje.coherencia(),
                formacion);
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
    @CacheEvict(cacheNames = CACHE_RANKING, allEntries = true)
    public void reemplazar(TipoRanking tipo, LocalDate fecha, List<PosicionRanking> posiciones) {
        TipoRankingJpa tipoJpa = mapper.toJpaTipo(tipo);
        repository.deleteByTipoAndFecha(tipoJpa, fecha);
        if (posiciones.isEmpty()) {
            return;
        }
        // Auditoría de consultas del 2026-11-02 (H-02): `saveAllAndFlush` de entidades con @IdClass y sin @Version hace `merge()` = un SELECT y un INSERT por
        // fila (≈ 10.800 viajes por noche con ~900 aprendices y seis tablas). Un INSERT por lote, con `ON CONFLICT` por si una persona se repitiera en la
        // lista (el corte es idempotente: la última posición gana).
        jdbcTemplate.batchUpdate(SQL_INSERTAR_POSICION, posiciones, TAMANO_DEL_LOTE, (ps, posicion) -> {
            ps.setObject(1, posicion.fecha());
            ps.setString(2, tipoJpa.name());
            ps.setObject(3, posicion.participanteId().value());
            ps.setInt(4, posicion.posicion());
            ps.setBigDecimal(5, posicion.puntaje());
            ps.setString(6, posicion.formacion().codigo());
        });
    }

    /** El tipo es un ENUM nativo de Postgres: se manda su nombre como texto y se castea (el mismo valor que el `@Enumerated(STRING)` de la entidad). */
    private static final String SQL_INSERTAR_POSICION = """
            INSERT INTO renaser.ranking_aprendices (fecha, tipo, participante_id, posicion, puntaje, formacion)
            VALUES (?, CAST(? AS renaser.tipo_ranking), ?, ?, ?, ?)
            ON CONFLICT (fecha, tipo, participante_id) DO UPDATE
               SET posicion = EXCLUDED.posicion, puntaje = EXCLUDED.puntaje, formacion = EXCLUDED.formacion
            """;

    /** Filas por lote de JDBC: acota la memoria del servidor y deja que el driver reescriba cada lote en pocas sentencias. */
    private static final int TAMANO_DEL_LOTE = 500;

    /**
     * Cacheado en memoria (D-63): es el hot path que muchas personas miran a la vez y que
     * cambia poco (1 snapshot por dia). La clave INCLUYE tipo y fecha — los dos parametros
     * que cambian el resultado — para no servirle a alguien el ranking de otra consulta.
     * Nunca el actor: esta consulta no varia segun quien pregunta (la posicion propia no se
     * marca aca, ver {@code RankingAgregadoService}), asi que el actor NO forma parte de la
     * clave a proposito.
     */
    @Override
    @Cacheable(cacheNames = CACHE_RANKING,
            key = "#tipo + '|' + #fecha + '|' + T(com.renaser.os.shared.infrastructure.persistence.FiltroDeFormacionSql).codigos(#alcance)")
    public List<EntradaRankingConNombre> porTipoYFecha(TipoRanking tipo, LocalDate fecha, AlcanceDeFormacion alcance) {
        List<RankingAprendizJpaEntity> filas = repository.findByTipoAndFechaAndFormacionInOrderByFormacionAscPosicionAsc(
                mapper.toJpaTipo(tipo), fecha, FiltroDeFormacionSql.codigos(alcance));

        // Una sola consulta de nombres para todo el listado: antes habia una por fila (N+1).
        Map<UserId, UserSummary> resumenes = userSummaryFinder.findByIds(
                filas.stream().map(mapper::participanteIdDe).toList());
        return filas.stream()
                .map(fila -> {
                    UserId participanteId = mapper.participanteIdDe(fila);
                    UserSummary resumen = resumenes.get(participanteId);
                    return new EntradaRankingConNombre(participanteId, resumen == null ? null : resumen.fullName(),
                            fila.getPosicion(), fila.getPuntaje(), Formacion.desde(fila.getFormacion()));
                })
                .toList();
    }
}
