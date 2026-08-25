package com.renaser.os.habits.infrastructure.adapter.out.persistence.registro;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DirtiesContext
class RegistroHabitoPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private RegistroHabitoPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private UserId participanteId;
    private HabitoId habitoId;

    @BeforeEach
    void seedFixtures() {
        participanteId = UserId.of(UUID.randomUUID());
        habitoId = HabitoId.newId();

        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture', 'APRENDIZ', 'ACTIVO')
                        """)
                .setParameter("id", participanteId.value())
                .setParameter("email", participanteId + "@renaser.test")
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                        VALUES (:usuarioId, 5)
                        """)
                .setParameter("usuarioId", participanteId.value())
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.habitos (id, ambito, titulo, tipo, categoria_clave)
                        VALUES (:id, 'SISTEMA', 'Meditar', 'CHECKBOX', 'MENTE')
                        """)
                .setParameter("id", habitoId.value())
                .executeUpdate();
    }

    private RegistroHabito nuevoPendiente(LocalDate fecha) {
        return RegistroHabito.generar(participanteId, habitoId, fecha, 5, TipoDia.DISCIPLINA, false, CLOCK.now());
    }

    @Test
    void guardaYRecuperaPorId() {
        RegistroHabito registro = adapter.save(nuevoPendiente(LocalDate.of(2026, 8, 24)));

        var recuperado = adapter.byId(registro.id());

        assertThat(recuperado).isPresent();
        assertThat(recuperado.get().estado()).isEqualTo(EstadoRegistro.PENDIENTE);
        assertThat(recuperado.get().habitoId()).isEqualTo(habitoId);
    }

    @Test
    void porParticipanteHabitoYFechaRespetaElUniqueDeNegocio() {
        adapter.save(nuevoPendiente(LocalDate.of(2026, 8, 24)));

        var encontrado = adapter.porParticipanteHabitoYFecha(participanteId, habitoId, LocalDate.of(2026, 8, 24));
        var noEncontrado = adapter.porParticipanteHabitoYFecha(participanteId, habitoId, LocalDate.of(2026, 8, 25));

        assertThat(encontrado).isPresent();
        assertThat(noEncontrado).isEmpty();
    }

    @Test
    void porParticipanteYFechaListaTodosLosDelDia() {
        adapter.save(nuevoPendiente(LocalDate.of(2026, 8, 24)));

        List<RegistroHabito> tracks = adapter.porParticipanteYFecha(participanteId, LocalDate.of(2026, 8, 24));

        assertThat(tracks).hasSize(1);
    }

    @Test
    void guardaCambiosDeEstadoYPuntosCompletados() {
        RegistroHabito registro = adapter.save(nuevoPendiente(LocalDate.of(2026, 8, 24)));
        registro.completar(9, "listo", 7, null, CLOCK.now());

        adapter.save(registro);
        var recuperado = adapter.byId(registro.id()).orElseThrow();

        assertThat(recuperado.estado()).isEqualTo(EstadoRegistro.COMPLETADO);
        assertThat(recuperado.puntosOtorgados()).isEqualTo(9);
        assertThat(recuperado.respuestaTexto()).isEqualTo("listo");
        assertThat(recuperado.calificacionProductividad()).isEqualTo(7);
    }

    @Test
    void enEstadoConFechaAnteriorATraeSoloLoAnteriorYEnEseEstado() {
        adapter.save(nuevoPendiente(LocalDate.of(2026, 8, 20))); // anterior, PENDIENTE
        RegistroHabito hoy = adapter.save(nuevoPendiente(LocalDate.of(2026, 8, 24))); // no anterior

        List<RegistroHabito> vencidos = adapter.enEstadoConFechaAnteriorA(EstadoRegistro.PENDIENTE,
                LocalDate.of(2026, 8, 24));

        assertThat(vencidos).hasSize(1);
        assertThat(vencidos.get(0).fechaEjecucion()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(vencidos).noneMatch(r -> r.id().equals(hoy.id()));
    }
}
