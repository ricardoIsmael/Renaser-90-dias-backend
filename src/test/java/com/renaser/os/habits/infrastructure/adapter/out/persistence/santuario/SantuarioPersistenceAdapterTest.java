package com.renaser.os.habits.infrastructure.adapter.out.persistence.santuario;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.santuario.EstadoRacha;
import com.renaser.os.habits.domain.model.santuario.EstadoSesionBloqueo;
import com.renaser.os.habits.domain.model.santuario.MotivoSalidaBloqueo;
import com.renaser.os.habits.domain.model.santuario.RachaSinCelular;
import com.renaser.os.habits.domain.model.santuario.SesionBloqueo;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DirtiesContext
class SantuarioPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T20:00:00Z"));

    @Autowired
    private SaveRegistroHabitoPort registroAdapter;
    @Autowired
    private SesionBloqueoPersistenceAdapter sesionAdapter;
    @Autowired
    private RachaSinCelularPersistenceAdapter rachaAdapter;
    @Autowired
    private EntityManager entityManager;

    private UserId participanteId;
    private RegistroHabito registro;

    @BeforeEach
    void seedFixtures() {
        participanteId = UserId.of(UUID.randomUUID());
        HabitoId habitoId = HabitoId.newId();

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
                        VALUES (:id, 'SISTEMA', 'Santuario', 'BLOQUEO', 'MENTE')
                        """)
                .setParameter("id", habitoId.value())
                .executeUpdate();

        registro = registroAdapter.save(RegistroHabito.generar(participanteId, habitoId,
                LocalDate.of(2026, 8, 24), 5, TipoDia.DISCIPLINA, false, CLOCK.now()));
    }

    @Test
    void guardaYRecuperaUnaSesionBloqueoActiva() {
        SesionBloqueo sesion = sesionAdapter.save(SesionBloqueo.iniciar(registro.id(), CLOCK.now()));

        var recuperada = sesionAdapter.porRegistro(registro.id());

        assertThat(recuperada).isPresent();
        assertThat(recuperada.get().estado()).isEqualTo(EstadoSesionBloqueo.ACTIVA);
        assertThat(recuperada.get().duracionMinimaMin()).isEqualTo(SesionBloqueo.DURACION_MINIMA_DEFAULT_MIN);
    }

    @Test
    void guardaUnaSesionRotaConMotivoYEvidencia() {
        SesionBloqueo sesion = SesionBloqueo.iniciar(registro.id(), CLOCK.now());
        sesion.romper(MotivoSalidaBloqueo.VIOLACION_APP_USADA, "evidencias", "ruta/foto.jpg",
                CLOCK.now().plus(Duration.ofMinutes(5)));

        sesionAdapter.save(sesion);
        var recuperada = sesionAdapter.porRegistro(registro.id()).orElseThrow();

        assertThat(recuperada.estado()).isEqualTo(EstadoSesionBloqueo.ROTA);
        assertThat(recuperada.motivoSalida()).isEqualTo(MotivoSalidaBloqueo.VIOLACION_APP_USADA);
        assertThat(recuperada.evidenciaSalidaBucket()).isEqualTo("evidencias");
        assertThat(recuperada.penalizacionAplicada()).isTrue();
    }

    @Test
    void guardaYRecuperaLaRachaActivaDeUnParticipante() {
        RachaSinCelular racha = rachaAdapter.save(
                RachaSinCelular.iniciar(participanteId, registro.id(), 24, CLOCK.now()));

        var activa = rachaAdapter.activaDe(participanteId);

        assertThat(activa).isPresent();
        assertThat(activa.get().id()).isEqualTo(racha.id());
        assertThat(activa.get().estado()).isEqualTo(EstadoRacha.ACTIVA);
        assertThat(activa.get().horasObjetivo()).isEqualTo(24);
    }

    @Test
    void unaRachaCompletadaYaNoApareceComoActiva() {
        RachaSinCelular racha = RachaSinCelular.iniciar(participanteId, registro.id(), 24,
                CLOCK.now().minus(Duration.ofHours(24)));
        racha.cerrar(CLOCK.now());
        rachaAdapter.save(racha);

        assertThat(rachaAdapter.activaDe(participanteId)).isEmpty();
    }

    @Test
    void activasDeFiltraSoloLosParticipantesConRachaViva() {
        rachaAdapter.save(RachaSinCelular.iniciar(participanteId, registro.id(), 24, CLOCK.now()));
        UserId otroSinRacha = UserId.of(UUID.randomUUID());

        List<RachaSinCelular> activas = rachaAdapter.activasDe(List.of(participanteId, otroSinRacha));

        assertThat(activas).hasSize(1);
        assertThat(activas.get(0).participanteId()).isEqualTo(participanteId);
    }
}
