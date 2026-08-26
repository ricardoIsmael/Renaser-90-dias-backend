package com.renaser.os.points.infrastructure.adapter.out.persistence.puntaje;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.points.application.ports.out.ajuste.SaveAjustePort;
import com.renaser.os.points.application.ports.out.puntaje.SavePuntajePort;
import com.renaser.os.points.domain.model.ajuste.AjustePuntos;
import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.points.domain.model.ajuste.ResultadoAjuste;
import com.renaser.os.points.domain.model.puntaje.PuntajeParticipante;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P-06: el saldo cacheado en puntajes_participante debe ser siempre
 * 100 + SUM(ajustes_puntos_liga.delta_aplicado). La vista renaser.verificacion_puntos_liga
 * existe justamente para detectar divergencias entre el saldo y su ledger — debe devolver
 * 0 filas siempre, sin importar cuantos ajustes se hayan aplicado ni si alguno toco el piso 0.
 *
 * Este test replica, ajuste por ajuste, exactamente lo que hace
 * PuntajeService.aplicarAjusteSobre (cargar, registrarAjuste sobre el agregado, guardar el
 * saldo Y el asiento) para no depender de mocks: cada delta se aplica contra el agregado en
 * memoria y ambos adapters escriben a Postgres real.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class VerificacionPuntosLigaViewTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private SavePuntajePort savePuntajePort;
    @Autowired
    private SaveAjustePort saveAjustePort;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    private UUID participanteId;

    @BeforeEach
    void crearPrerrequisitos() {
        participanteId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol)
                VALUES (?, ?, 'Aprendiz de Prueba', 'APRENDIZ')
                """, participanteId, "aprendiz-" + participanteId + "@renaser.com");
        jdbcTemplate.update("INSERT INTO renaser.participantes_programa (usuario_id) VALUES (?)", participanteId);
    }

    private void aplicarAjuste(PuntajeParticipante puntaje, MotivoPuntos motivo, int delta) {
        ResultadoAjuste resultado = puntaje.registrarAjuste(delta, CLOCK);
        savePuntajePort.save(puntaje);
        saveAjustePort.save(AjustePuntos.registrar(puntaje.participanteId(), motivo, resultado, null, CLOCK));
    }

    @Test
    @DisplayName("P-06: tras varios ajustes, incluyendo uno que toca el piso 0, la vista de verificacion no reporta divergencias")
    void elSaldoCacheadoNuncaDivergeDelLedgerAunqueSeToqueElPiso() {
        PuntajeParticipante puntaje = PuntajeParticipante.inicial(UserId.of(participanteId), CLOCK);

        aplicarAjuste(puntaje, MotivoPuntos.HABIT_COMPLETED, 10);       // 100 -> 110
        aplicarAjuste(puntaje, MotivoPuntos.ROCK_COMPLETED, 25);        // 110 -> 135
        aplicarAjuste(puntaje, MotivoPuntos.MISSED_HABIT, -1000);       // 135 -> 0, piso: deltaAplicado real = -135
        aplicarAjuste(puntaje, MotivoPuntos.MISSED_HABIT, -50);         // ya en 0, deltaAplicado 0
        aplicarAjuste(puntaje, MotivoPuntos.STREAK_BONUS, 5);           // 0 -> 5
        aplicarAjuste(puntaje, MotivoPuntos.MANUAL_ADJUSTMENT, -3);     // 5 -> 2

        assertThat(puntaje.puntosLiga()).isEqualTo(2);

        // AjustePuntosPersistenceAdapter.save() no hace flush (los asientos son append-only,
        // sin necesidad de leer su propio estado de vuelta) -- hay que forzarlo aca para que la
        // consulta SQL nativa de mas abajo (que no pasa por el EntityManager) vea los INSERT
        // pendientes, si no la comparacion seria contra un ledger todavia no escrito en Postgres.
        entityManager.flush();

        List<Map<String, Object>> divergencias = jdbcTemplate.queryForList(
                "SELECT * FROM renaser.verificacion_puntos_liga WHERE participante_id = ?", participanteId);

        assertThat(divergencias).isEmpty();
    }

    @Test
    @DisplayName("P-06: un participante sin ningun ajuste (saldo inicial 100, ledger vacio) tampoco diverge")
    void unParticipanteSinAjustesNoDiverge() {
        savePuntajePort.save(PuntajeParticipante.inicial(UserId.of(participanteId), CLOCK));
        entityManager.flush();

        List<Map<String, Object>> divergencias = jdbcTemplate.queryForList(
                "SELECT * FROM renaser.verificacion_puntos_liga WHERE participante_id = ?", participanteId);

        assertThat(divergencias).isEmpty();
    }
}
