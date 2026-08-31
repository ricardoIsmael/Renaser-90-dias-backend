package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CambioHorarioPendiente;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Faltaba (CLAUDE.MD §0.2 la exige para todo adaptador de persistencia) y su ausencia es la razon
 * por la que E-53 y E-54 sobrevivieron: la unica prueba del camino diferido era con mocks, que por
 * definicion no ven ni la FK ni las consultas reales.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CambioHorarioPendientePersistenceAdapterTest {

    private static final Instant AHORA = Instant.parse("2026-08-24T09:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 8, 24);

    @Autowired
    private CambioHorarioPendientePersistenceAdapter adapter;
    @Autowired
    private PreferenciaHorarioPersistenceAdapter preferenciaAdapter;
    @Autowired
    private EntityManager entityManager;

    private UserId participanteId;
    private HabitoId habitoId;

    @BeforeEach
    void seedFixtures() {
        participanteId = nuevoParticipante();
        habitoId = nuevoHabito("Meditar " + UUID.randomUUID());
    }

    @Test
    void guardaYRecuperaUnCambioPendiente() {
        seedPreferencia(participanteId, habitoId);
        CambioHorarioPendiente pendiente = CambioHorarioPendiente.programar(participanteId, habitoId,
                LocalTime.of(7, 0), LocalTime.of(9, 0), true, 15, HOY.plusDays(1), AHORA);

        adapter.save(pendiente);
        var recuperado = adapter.porParticipanteYHabito(participanteId, habitoId);

        assertThat(recuperado).isPresent();
        assertThat(recuperado.get().horaDisparo()).isEqualTo(LocalTime.of(7, 0));
        assertThat(recuperado.get().horaLimite()).isEqualTo(LocalTime.of(9, 0));
        assertThat(recuperado.get().recordatorioActivo()).isTrue();
        assertThat(recuperado.get().minutosRecordatorio()).isEqualTo(15);
        assertThat(recuperado.get().fechaEfectiva()).isEqualTo(HOY.plusDays(1));
        assertThat(recuperado.get().creadoEn()).isEqualTo(AHORA);
    }

    @Test
    void deParticipanteDevuelveSoloLosDelParticipante() {
        seedPreferencia(participanteId, habitoId);
        adapter.save(pendienteEn(participanteId, habitoId, HOY.plusDays(1)));
        UserId otro = nuevoParticipante();
        HabitoId otroHabito = nuevoHabito("Leer " + UUID.randomUUID());
        seedPreferencia(otro, otroHabito);
        adapter.save(pendienteEn(otro, otroHabito, HOY.plusDays(1)));

        List<CambioHorarioPendiente> mios = adapter.deParticipante(participanteId);

        assertThat(mios).hasSize(1);
        assertThat(mios.get(0).habitoId()).isEqualTo(habitoId);
    }

    @Test
    void queYaRigenEnTraeLosVencidosDeCualquierParticipanteYDejaAfueraLosFuturos() {
        seedPreferencia(participanteId, habitoId);
        adapter.save(pendienteEn(participanteId, habitoId, HOY.minusDays(1)));   // atrasado: rige
        UserData deHoy = nuevoParticipanteConHabito("Correr " + UUID.randomUUID());
        adapter.save(pendienteEn(deHoy.participanteId(), deHoy.habitoId(), HOY)); // justo hoy: rige
        UserData futuro = nuevoParticipanteConHabito("Escribir " + UUID.randomUUID());
        adapter.save(pendienteEn(futuro.participanteId(), futuro.habitoId(), HOY.plusDays(1))); // manana: no

        List<CambioHorarioPendiente> vencidos = adapter.queYaRigenEn(HOY);

        assertThat(vencidos).extracting(CambioHorarioPendiente::habitoId)
                .contains(habitoId, deHoy.habitoId())
                .doesNotContain(futuro.habitoId());
    }

    /**
     * E-54: {@code cambios_horario_pendientes} tiene FK compuesta a {@code preferencias_horario}.
     * Sin fila padre el INSERT es imposible — por eso la rama diferida tiene que crearla antes.
     */
    @Test
    void sinFilaEnPreferenciasHorarioLaFkRechazaElPendiente() {
        assertThatThrownBy(() -> adapter.save(pendienteEn(participanteId, habitoId, HOY.plusDays(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void borrarLoInexistenteNoFalla() {
        assertThatCode(() -> adapter.borrar(participanteId, habitoId)).doesNotThrowAnyException();
    }

    @Test
    void borrarQuitaElPendiente() {
        seedPreferencia(participanteId, habitoId);
        adapter.save(pendienteEn(participanteId, habitoId, HOY.plusDays(1)));

        adapter.borrar(participanteId, habitoId);

        assertThat(adapter.porParticipanteYHabito(participanteId, habitoId)).isEmpty();
    }

    private static CambioHorarioPendiente pendienteEn(UserId participanteId, HabitoId habitoId,
                                                        LocalDate fechaEfectiva) {
        return CambioHorarioPendiente.programar(participanteId, habitoId, LocalTime.of(6, 30), LocalTime.of(8, 30),
                false, null, fechaEfectiva, AHORA);
    }

    private UserData nuevoParticipanteConHabito(String titulo) {
        UserId nuevoParticipante = nuevoParticipante();
        HabitoId nuevoHabito = nuevoHabito(titulo);
        seedPreferencia(nuevoParticipante, nuevoHabito);
        return new UserData(nuevoParticipante, nuevoHabito);
    }

    private void seedPreferencia(UserId participante, HabitoId habito) {
        preferenciaAdapter.save(PreferenciaHorario.crear(participante, habito, LocalTime.of(8, 0),
                LocalTime.of(10, 0), AHORA));
    }

    private UserId nuevoParticipante() {
        UserId id = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture', 'APRENDIZ', 'ACTIVO')
                        """)
                .setParameter("id", id.value())
                .setParameter("email", id + "@renaser.test")
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                        VALUES (:usuarioId, 10)
                        """)
                .setParameter("usuarioId", id.value())
                .executeUpdate();
        return id;
    }

    private HabitoId nuevoHabito(String titulo) {
        HabitoId id = HabitoId.newId();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.habitos (id, ambito, titulo, tipo, categoria_clave)
                        VALUES (:id, 'SISTEMA', :titulo, 'CHECKBOX', 'MENTE')
                        """)
                .setParameter("id", id.value())
                .setParameter("titulo", titulo)
                .executeUpdate();
        return id;
    }

    private record UserData(UserId participanteId, HabitoId habitoId) {
    }
}
