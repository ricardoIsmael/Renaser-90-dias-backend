package com.renaser.os.points.infrastructure.adapter.out.persistence.puntaje;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.points.domain.model.puntaje.PuntajeParticipante;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PuntajeParticipantePersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private PuntajeParticipantePersistenceAdapter adapter;
    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Test
    void guardaYRecuperaUnPuntajeNuevo() {
        PuntajeParticipante puntaje = PuntajeParticipante.inicial(UserId.of(participanteId), CLOCK);
        puntaje.registrarAjuste(25, CLOCK);

        adapter.save(puntaje);

        PuntajeParticipante recuperado = adapter.byParticipanteId(UserId.of(participanteId)).orElseThrow();
        assertThat(recuperado.puntosLiga()).isEqualTo(125);
        assertThat(recuperado.coherencia()).isEqualByComparingTo("100");
    }

    @Test
    void guardarDosVecesActualizaLaMismaFila_noDuplica() {
        PuntajeParticipante puntaje = PuntajeParticipante.inicial(UserId.of(participanteId), CLOCK);
        adapter.save(puntaje);

        puntaje.actualizarCoherencia(new BigDecimal("42.50"), CLOCK);
        adapter.save(puntaje);

        PuntajeParticipante recuperado = adapter.byParticipanteId(UserId.of(participanteId)).orElseThrow();
        assertThat(recuperado.coherencia()).isEqualByComparingTo("42.50");
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM renaser.puntajes_participante WHERE participante_id = ?", Long.class,
                participanteId);
        assertThat(total).isEqualTo(1L);
    }

    @Test
    void unParticipanteSinFilaTodaviaNoEstaPresente() {
        assertThat(adapter.byParticipanteId(UserId.of(UUID.randomUUID()))).isEmpty();
    }
}
