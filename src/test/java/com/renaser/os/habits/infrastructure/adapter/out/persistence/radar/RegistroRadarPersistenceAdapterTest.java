package com.renaser.os.habits.infrastructure.adapter.out.persistence.radar;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DirtiesContext
class RegistroRadarPersistenceAdapterTest {

    private static final Instant AHORA = Instant.parse("2026-08-24T14:00:00Z");

    @Autowired
    private RegistroRadarPersistenceAdapter adapter;
    @Autowired
    private EntityManager entityManager;

    private UserId participanteId;

    @BeforeEach
    void seedFixtures() {
        participanteId = UserId.of(UUID.randomUUID());
        seedParticipante(participanteId);
    }

    private void seedParticipante(UserId id) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture', 'APRENDIZ', 'ACTIVO')
                        """)
                .setParameter("id", id.value())
                .setParameter("email", id + "@renaser.test")
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                        VALUES (:usuarioId, 5)
                        """)
                .setParameter("usuarioId", id.value())
                .executeUpdate();
    }

    @Test
    void guardaYRecuperaUnRegistro() {
        RegistroRadar registro = RegistroRadar.registrar(participanteId, "haciendo", "pensando", "sintiendo", 7,
                "evitando", AHORA);

        adapter.save(registro);
        var ultimo = adapter.ultimoDeParticipante(participanteId);

        assertThat(ultimo).isPresent();
        assertThat(ultimo.get().id()).isEqualTo(registro.id());
        assertThat(ultimo.get().queHago()).isEqualTo("haciendo");
        assertThat(ultimo.get().quePienso()).isEqualTo("pensando");
        assertThat(ultimo.get().queSiento()).isEqualTo("sintiendo");
        assertThat(ultimo.get().nivelEnergia()).isEqualTo(7);
        assertThat(ultimo.get().queEvito()).isEqualTo("evitando");
        assertThat(ultimo.get().creadoEn()).isEqualTo(AHORA);
    }

    @Test
    void ultimoDeParticipanteDevuelveElMasReciente() {
        adapter.save(RegistroRadar.registrar(participanteId, "h1", "p1", "s1", 3, "e1",
                AHORA.minus(2, ChronoUnit.HOURS)));
        RegistroRadar masReciente = RegistroRadar.registrar(participanteId, "h2", "p2", "s2", 8, "e2", AHORA);
        adapter.save(masReciente);

        var ultimo = adapter.ultimoDeParticipante(participanteId);

        assertThat(ultimo).isPresent();
        assertThat(ultimo.get().id()).isEqualTo(masReciente.id());
    }

    @Test
    void ultimoDeParticipanteVacioSinRegistros() {
        assertThat(adapter.ultimoDeParticipante(participanteId)).isEmpty();
    }

    @Test
    void historialOrdenaDescendenteYRespetaElTamanoDePagina() {
        adapter.save(RegistroRadar.registrar(participanteId, "h1", "p", "s", 1, "e",
                AHORA.minus(3, ChronoUnit.HOURS)));
        RegistroRadar medio = RegistroRadar.registrar(participanteId, "h2", "p", "s", 2, "e",
                AHORA.minus(2, ChronoUnit.HOURS));
        adapter.save(medio);
        RegistroRadar masNuevo = RegistroRadar.registrar(participanteId, "h3", "p", "s", 3, "e", AHORA);
        adapter.save(masNuevo);

        List<RegistroRadar> pagina = adapter.historialDeParticipante(participanteId, null, 2);

        assertThat(pagina).hasSize(2);
        assertThat(pagina.get(0).id()).isEqualTo(masNuevo.id());
        assertThat(pagina.get(1).id()).isEqualTo(medio.id());
    }

    @Test
    void historialFiltraPorCursorTraeSoloLosMasViejos() {
        RegistroRadar viejo = RegistroRadar.registrar(participanteId, "h1", "p", "s", 1, "e",
                AHORA.minus(2, ChronoUnit.HOURS));
        adapter.save(viejo);
        Instant cursor = AHORA.minus(1, ChronoUnit.HOURS);
        adapter.save(RegistroRadar.registrar(participanteId, "h2", "p", "s", 2, "e", cursor));
        adapter.save(RegistroRadar.registrar(participanteId, "h3", "p", "s", 3, "e", AHORA));

        List<RegistroRadar> pagina = adapter.historialDeParticipante(participanteId, cursor, 10);

        assertThat(pagina).hasSize(1);
        assertThat(pagina.get(0).id()).isEqualTo(viejo.id());
    }

    @Test
    void historialFiltraSoloLosRegistrosDelParticipante() {
        UserId otroParticipante = UserId.of(UUID.randomUUID());
        seedParticipante(otroParticipante);
        adapter.save(RegistroRadar.registrar(participanteId, "mio", "p", "s", 5, "e", AHORA));
        adapter.save(RegistroRadar.registrar(otroParticipante, "ajeno", "p", "s", 5, "e", AHORA));

        List<RegistroRadar> pagina = adapter.historialDeParticipante(participanteId, null, 10);

        assertThat(pagina).hasSize(1);
        assertThat(pagina.get(0).queHago()).isEqualTo("mio");
    }
}
