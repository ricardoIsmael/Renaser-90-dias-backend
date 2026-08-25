package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocadiaria;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DirtiesContext
class RocaDiariaPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private RocaDiariaPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private UserId participanteId;

    @BeforeEach
    void seedParticipante() {
        participanteId = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, :nombre, CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .setParameter("id", participanteId.value())
                .setParameter("email", participanteId + "@renaser.test")
                .setParameter("nombre", "Fixture")
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                        VALUES (:id, 20)
                        """)
                .setParameter("id", participanteId.value())
                .executeUpdate();
    }

    @Test
    void guardaYRecuperaUnaRocaDiariaPorFecha() {
        RocaDiaria roca = RocaDiaria.planificar(participanteId, LocalDate.of(2026, 8, 25), 1, "titulo", "desc", 8,
                false, EjeObjetivo.CUERPO, null, LocalTime.of(18, 0), LocalTime.of(20, 0), CLOCK);

        adapter.save(roca);

        List<RocaDiaria> delDia = adapter.deParticipanteYFecha(participanteId, LocalDate.of(2026, 8, 25));
        assertThat(delDia).hasSize(1);
        assertThat(delDia.get(0).titulo()).isEqualTo("titulo");
        assertThat(delDia.get(0).horaFin()).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    void contarDeParticipanteYFechaCuentaCorrectamente() {
        LocalDate fecha = LocalDate.of(2026, 8, 26);
        adapter.saveAll(List.of(
                RocaDiaria.planificar(participanteId, fecha, 1, "verde", null, 5, false, EjeObjetivo.CUERPO, null,
                        null, null, CLOCK),
                RocaDiaria.planificar(participanteId, fecha, 2, "amarilla", null, 5, false, EjeObjetivo.CUERPO, null,
                        null, null, CLOCK)));

        assertThat(adapter.contarDeParticipanteYFecha(participanteId, fecha)).isEqualTo(2);
        assertThat(adapter.contarDeParticipanteYFecha(participanteId, fecha.plusDays(1))).isEqualTo(0);
    }

    @Test
    void completarYGuardarPersisteElEstado() {
        RocaDiaria roca = RocaDiaria.planificar(participanteId, LocalDate.of(2026, 8, 27), 1, "t", null, 5, false,
                EjeObjetivo.TRABAJO, null, null, null, CLOCK);
        roca = adapter.save(roca);

        roca.completar(CLOCK.now(), CLOCK);
        roca.otorgarPuntos(10);
        adapter.save(roca);
        entityManager.flush();
        entityManager.clear();

        var recuperada = adapter.byId(roca.id());
        assertThat(recuperada).isPresent();
        assertThat(recuperada.get().completada()).isTrue();
        assertThat(recuperada.get().puntosOtorgados()).isEqualTo(10);
    }
}
