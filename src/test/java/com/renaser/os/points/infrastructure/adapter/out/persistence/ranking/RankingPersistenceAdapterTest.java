package com.renaser.os.points.infrastructure.adapter.out.persistence.ranking;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingCandidatosPort.CandidatoRanking;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DirtiesContext
class RankingPersistenceAdapterTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 8, 24);

    @Autowired
    private RankingPersistenceAdapter adapter;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID aprendizActivoId;

    @BeforeEach
    void crearUsuarios() {
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
        List<CandidatoRanking> candidatos = adapter.aprendicesActivosConPuntaje();

        assertThat(candidatos).extracting(CandidatoRanking::participanteId)
                .containsExactly(UserId.of(aprendizActivoId));
    }

    @Test
    void reemplazarEsIdempotente_noDuplicaAlCorrerDosVeces() {
        List<PosicionRanking> posiciones = List.of(
                new PosicionRanking(FECHA, TipoRanking.LEAGUE, UserId.of(aprendizActivoId), 1, new BigDecimal("130")));

        adapter.reemplazar(TipoRanking.LEAGUE, FECHA, posiciones);
        adapter.reemplazar(TipoRanking.LEAGUE, FECHA, posiciones);

        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM renaser.ranking_aprendices WHERE tipo = 'LIGA' AND fecha = ?", Long.class,
                FECHA);
        assertThat(total).isEqualTo(1L);
    }

    @Test
    void porTipoYFechaDevuelveLasFilasConNombreOrdenadasPorPosicion() {
        adapter.reemplazar(TipoRanking.LEAGUE, FECHA, List.of(
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
        adapter.reemplazar(tipo, FECHA, List.of(
                new PosicionRanking(FECHA, tipo, UserId.of(aprendizActivoId), 1, new BigDecimal("42"))));

        var filas = adapter.porTipoYFecha(tipo, FECHA);

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).participanteId()).isEqualTo(UserId.of(aprendizActivoId));
    }
}
