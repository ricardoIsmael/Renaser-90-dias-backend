package com.renaser.os.points.infrastructure.adapter.out.persistence.ranking;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingCandidatosPort;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingCandidatosPort.CandidatoRanking;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingPort;
import com.renaser.os.points.application.ports.out.ranking.SaveRankingSnapshotPort;
import com.renaser.os.points.domain.model.ranking.PosicionRanking;
import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RankingPersistenceAdapterTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 8, 24);

    // Autowireados por INTERFAZ (puerto), no por la clase concreta: con @Cacheable activo
    // (D-63) Spring envuelve el bean en un proxy JDK dinamico que implementa los puertos
    // publicos, no RankingPersistenceAdapter en si — un @Autowired de la clase concreta
    // fallaria con UnsatisfiedDependencyException apenas se habilito el cache.
    @Autowired
    private LoadRankingCandidatosPort adapterCandidatos;
    @Autowired
    private SaveRankingSnapshotPort adapterEscritura;
    @Autowired
    private LoadRankingPort adapter;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CacheManager cacheManager;

    private UUID aprendizActivoId;

    @BeforeEach
    void crearUsuarios() {
        // porTipoYFecha() ahora esta cacheado (D-63). El cache vive en el bean, no en la
        // transaccion: sin este clear(), una entrada que quedo cacheada en un test anterior
        // (con datos que el @Transactional de ESE test ya deshizo) se filtraria a este,
        // porque el rollback de Postgres no sabe nada de Caffeine.
        cacheManager.getCacheNames().forEach(nombre -> cacheManager.getCache(nombre).clear());
        aprendizActivoId = crearParticipante("APRENDIZ", "ACTIVO", 130, "77.50");
        crearParticipante("APRENDIZ", "SUSPENDIDO", 999, "99.00"); // suspendido, no debe aparecer
        crearParticipante("MENTOR", "ACTIVO", 500, "50.00"); // rol distinto, no debe aparecer
    }

    private UUID crearParticipante(String rol, String estado, int puntosLiga, String coherencia) {
        UUID id = crearUsuarioSinParticipante(rol, estado);
        jdbcTemplate.update("INSERT INTO renaser.participantes_programa (usuario_id) VALUES (?)", id);
        jdbcTemplate.update("""
                INSERT INTO renaser.puntajes_participante (participante_id, puntos_liga, coherencia)
                VALUES (?, ?, ?::numeric)
                """, id, puntosLiga, coherencia);
        return id;
    }

    private UUID crearUsuarioSinParticipante(String rol, String estado) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Usuario de Prueba', ?::renaser.rol_usuario, ?::renaser.estado_usuario)
                """, id, "u-" + id + "@renaser.com", rol, estado);
        return id;
    }

    @Test
    void aprendicesActivosConPuntajeFiltraPorRolYEstado() {
        List<CandidatoRanking> candidatos = adapterCandidatos.aprendicesActivosConPuntaje();

        assertThat(candidatos).extracting(CandidatoRanking::participanteId)
                .containsExactly(UserId.of(aprendizActivoId));
    }

    @Test
    void reemplazarEsIdempotente_noDuplicaAlCorrerDosVeces() {
        List<PosicionRanking> posiciones = List.of(
                new PosicionRanking(FECHA, TipoRanking.LEAGUE, UserId.of(aprendizActivoId), 1, new BigDecimal("130")));

        adapterEscritura.reemplazar(TipoRanking.LEAGUE, FECHA, posiciones);
        adapterEscritura.reemplazar(TipoRanking.LEAGUE, FECHA, posiciones);

        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM renaser.ranking_aprendices WHERE tipo = 'LIGA' AND fecha = ?", Long.class,
                FECHA);
        assertThat(total).isEqualTo(1L);
    }

    @Test
    void porTipoYFechaDevuelveLasFilasConNombreOrdenadasPorPosicion() {
        adapterEscritura.reemplazar(TipoRanking.LEAGUE, FECHA, List.of(
                new PosicionRanking(FECHA, TipoRanking.LEAGUE, UserId.of(aprendizActivoId), 1, new BigDecimal("130"))));

        var filas = adapter.porTipoYFecha(TipoRanking.LEAGUE, FECHA);

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).participanteId()).isEqualTo(UserId.of(aprendizActivoId));
        assertThat(filas.get(0).fullName()).isEqualTo("Usuario de Prueba");
        assertThat(filas.get(0).posicion()).isEqualTo(1);
    }

    @Test
    void porTipoYFechaSinSnapshotDevuelveVacio() {
        assertThat(adapter.porTipoYFecha(TipoRanking.CELL, FECHA)).isEmpty();
    }

    @DisplayName("traduce los 4 valores de TipoRanking en ambas direcciones (GENERAL/COHORT no se generan hoy, pero el adaptador ya los soporta)")
    @ParameterizedTest
    @EnumSource(TipoRanking.class)
    void traduceLosCuatroTiposDeRankingEnAmbasDirecciones(TipoRanking tipo) {
        adapterEscritura.reemplazar(tipo, FECHA, List.of(
                new PosicionRanking(FECHA, tipo, UserId.of(aprendizActivoId), 1, new BigDecimal("42"))));

        var filas = adapter.porTipoYFecha(tipo, FECHA);

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).participanteId()).isEqualTo(UserId.of(aprendizActivoId));
    }
}
