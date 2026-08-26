package com.renaser.os.rag.infrastructure.adapter.out.persistence.espejosombra;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rag.application.ports.out.espejosombra.LoadInformeEspejoSombraPort;
import com.renaser.os.rag.application.ports.out.espejosombra.SaveInformeEspejoSombraPort;
import com.renaser.os.rag.domain.model.espejosombra.DistribucionTemporal;
import com.renaser.os.rag.domain.model.espejosombra.InformeEspejoSombra;
import com.renaser.os.rag.domain.model.espejosombra.PreguntaConfrontacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistencia del agregado Espejo Sombra contra Postgres real (Testcontainers) —
 * cubre el @ElementCollection de {@code preguntas_confrontacion} (mismo riesgo real
 * que {@code RocaSemanalPersistenceAdapterTest} con {@code acciones_criticas}), el
 * UNIQUE {@code (participante_id, semana_inicio)} y que el CHECK
 * {@code pcts_suman_100} de la base rechaza una fila inválida insertada por SQL
 * nativo — la última línea de defensa de la que habla el javadoc de
 * {@code DistribucionTemporal}, verificada contra el motor real, no simulada.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InformeEspejoSombraPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private LoadInformeEspejoSombraPort loadPort;
    @Autowired
    private SaveInformeEspejoSombraPort savePort;
    @Autowired
    private EntityManager entityManager;

    private UserId participanteId;

    @BeforeEach
    void seedParticipante() {
        participanteId = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .setParameter("id", participanteId.value())
                .setParameter("email", participanteId + "@renaser.test")
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                        VALUES (:id, 20)
                        """)
                .setParameter("id", participanteId.value())
                .executeUpdate();
    }

    private static List<PreguntaConfrontacion> tresPreguntas() {
        return List.of(new PreguntaConfrontacion(1, "Que evitaste esta semana?"),
                new PreguntaConfrontacion(2, "Que repetiste sin darte cuenta?"),
                new PreguntaConfrontacion(3, "Que le dirias a tu yo de hace 90 dias?"));
    }

    @Test
    void guardaYRecuperaElInformeConSusPreguntasEnOrden() {
        InformeEspejoSombra informe = InformeEspejoSombra.generar(participanteId, LocalDate.of(2026, 8, 17),
                4, "Evitacion", new DistribucionTemporal(30, 50, 20), "insight de la semana", tresPreguntas(), CLOCK);

        savePort.save(informe);
        entityManager.flush();
        entityManager.clear();

        var recuperado = loadPort.byId(informe.id());
        assertThat(recuperado).isPresent();
        assertThat(recuperado.get().participanteId()).isEqualTo(participanteId);
        assertThat(recuperado.get().distribucion()).isEqualTo(new DistribucionTemporal(30, 50, 20));
        assertThat(recuperado.get().preguntas()).extracting(PreguntaConfrontacion::orden)
                .containsExactly(1, 2, 3);
        assertThat(recuperado.get().preguntas()).extracting(PreguntaConfrontacion::pregunta)
                .containsExactly("Que evitaste esta semana?", "Que repetiste sin darte cuenta?",
                        "Que le dirias a tu yo de hace 90 dias?");
    }

    @Test
    void porParticipanteYSemanaEncuentraElInformeDeEsaSemanaUnicamente() {
        LocalDate semana1 = LocalDate.of(2026, 8, 3);
        LocalDate semana2 = LocalDate.of(2026, 8, 10);
        savePort.save(InformeEspejoSombra.generar(participanteId, semana1, 2, "patron1",
                new DistribucionTemporal(10, 80, 10), "insight1", List.of(), CLOCK));
        entityManager.flush();
        entityManager.clear();

        assertThat(loadPort.porParticipanteYSemana(participanteId, semana1)).isPresent();
        assertThat(loadPort.porParticipanteYSemana(participanteId, semana2)).isEmpty();
    }

    @Test
    void deParticipanteDevuelveLosInformesMasRecientesPrimero() {
        LocalDate semana1 = LocalDate.of(2026, 8, 3);
        LocalDate semana2 = LocalDate.of(2026, 8, 10);
        savePort.save(InformeEspejoSombra.generar(participanteId, semana1, 2, "patron1",
                new DistribucionTemporal(10, 80, 10), "insight1", List.of(), CLOCK));
        savePort.save(InformeEspejoSombra.generar(participanteId, semana2, 3, "patron2",
                new DistribucionTemporal(20, 60, 20), "insight2", List.of(), CLOCK));
        entityManager.flush();
        entityManager.clear();

        var informes = loadPort.deParticipante(participanteId);
        assertThat(informes).hasSize(2);
        assertThat(informes).extracting(InformeEspejoSombra::semanaInicio).containsExactly(semana2, semana1);
    }

    /**
     * UNIQUE {@code (participante_id, semana_inicio)}: la idempotencia del scheduler
     * (ver {@code EspejoSombraService.generar}) se apoya en este constraint como
     * última línea de defensa, no solo en el chequeo previo de la aplicación.
     */
    @Test
    void noSePuedeGuardarDosInformesParaLaMismaSemana() {
        LocalDate semana = LocalDate.of(2026, 8, 3);
        savePort.save(InformeEspejoSombra.generar(participanteId, semana, 2, "patron1",
                new DistribucionTemporal(10, 80, 10), "insight1", List.of(), CLOCK));
        entityManager.flush();

        InformeEspejoSombra duplicado = InformeEspejoSombra.generar(participanteId, semana, 5, "patron2",
                new DistribucionTemporal(20, 60, 20), "insight2", List.of(), CLOCK);

        assertThatThrownBy(() -> {
            savePort.save(duplicado);
            entityManager.flush();
        }).isInstanceOf(RuntimeException.class);
    }

    /**
     * El CHECK {@code pcts_suman_100} es la última línea de defensa (javadoc de
     * {@link DistribucionTemporal}) — este test la ejercita saltando el dominio por
     * completo, con SQL nativo, tal como podría hacerlo una migración de datos o un
     * script manual mal escrito.
     */
    @Test
    void elCheckPctsSuman100RechazaUnaInsercionInvalidaPorSqlNativo() {
        UUID informeId = UUID.randomUUID();
        assertThatThrownBy(() -> {
            entityManager.createNativeQuery("""
                            INSERT INTO renaser.informes_espejo_sombra
                                (id, participante_id, semana_inicio, cantidad_entradas, patron_dominante,
                                 pct_pasado, pct_presente, pct_futuro, insight)
                            VALUES (:id, :pid, :semana, 3, 'patron', 40, 40, 40, 'insight invalido')
                            """)
                    .setParameter("id", informeId)
                    .setParameter("pid", participanteId.value())
                    .setParameter("semana", LocalDate.of(2026, 8, 3))
                    .executeUpdate();
            entityManager.flush();
        }).isInstanceOf(RuntimeException.class);
    }
}
