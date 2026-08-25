package com.renaser.os.points.infrastructure.adapter.out.persistence.ajuste;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.points.domain.model.ajuste.AjustePuntos;
import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.points.domain.model.ajuste.ResultadoAjuste;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** IT con Testcontainers — ver PuntajeParticipantePersistenceAdapterTest para el porque
 * de los INSERT manuales de prerrequisitos (usuarios/participantes_programa). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DirtiesContext
class AjustePuntosPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private AjustePuntosPersistenceAdapter adapter;
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
    void guardaUnAsientoYLeAsignaIdAutogenerado() {
        ResultadoAjuste resultado = new ResultadoAjuste(10, 10, 100, 110);
        AjustePuntos ajuste = AjustePuntos.registrar(UserId.of(participanteId), MotivoPuntos.HABIT_COMPLETED,
                resultado, "completado a tiempo", CLOCK);

        AjustePuntos guardado = adapter.save(ajuste);

        assertThat(guardado.id()).isNotNull();
        assertThat(guardado.deltaAplicado()).isEqualTo(10);
    }

    @Test
    void traduceLos12MotivosEnAmbasDirecciones() {
        for (MotivoPuntos motivo : MotivoPuntos.values()) {
            ResultadoAjuste resultado = new ResultadoAjuste(1, 1, 0, 1);
            AjustePuntos ajuste = AjustePuntos.registrar(UserId.of(participanteId), motivo, resultado, null, CLOCK);

            AjustePuntos guardado = adapter.save(ajuste);

            assertThat(guardado.motivo()).isEqualTo(motivo);
        }
    }
}
