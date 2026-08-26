package com.renaser.os.rocks.infrastructure.adapter.out.persistence.verdugo;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rocks.domain.model.verdugo.DestinoVerdugo;
import com.renaser.os.rocks.domain.model.verdugo.EventoVerdugo;
import com.renaser.os.rocks.domain.model.verdugo.ResultadoVerdugo;
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

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EventoVerdugoPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T21:00:00Z"));

    @Autowired
    private EventoVerdugoPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private UserId participanteId;
    private UUID rocaDiariaId;

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
        // eventos_verdugo.roca_diaria_id tiene FK real a rocas_diarias — hace falta una fila real,
        // no un UUID sin respaldo (ver docs/BITACORA_ERRORES.md).
        rocaDiariaId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.rocas_diarias
                            (id, participante_id, fecha, posicion, titulo, color, puntaje_impacto, eje)
                        VALUES (:id, :participanteId, :fecha, 1, 'Fixture', CAST('VERDE' AS renaser.color_pareto), 5,
                                CAST('CUERPO' AS renaser.eje_objetivo))
                        """)
                .setParameter("id", rocaDiariaId)
                .setParameter("participanteId", participanteId.value())
                .setParameter("fecha", LocalDate.of(2026, 8, 24))
                .executeUpdate();
    }

    @Test
    void guardaYRecuperaUnEventoResueltoPorElCliente() {
        EventoVerdugo evento = EventoVerdugo.registrar(participanteId, DestinoVerdugo.ROCA_DIARIA,
                rocaDiariaId, CLOCK.now(), ResultadoVerdugo.COMPLETADO, CLOCK);

        adapter.save(evento);

        List<EventoVerdugo> propios = adapter.deParticipante(participanteId);
        assertThat(propios).hasSize(1);
        assertThat(propios.get(0).resultado()).isEqualTo(ResultadoVerdugo.COMPLETADO);
        assertThat(propios.get(0).destinoTipo()).isEqualTo(DestinoVerdugo.ROCA_DIARIA);
    }

    @Test
    void pendientesDeFechaEncuentraSoloLosSinResolverDeEseDia() {
        EventoVerdugo pendiente = EventoVerdugo.rehydrate(
                com.renaser.os.rocks.domain.model.verdugo.EventoVerdugoId.newId(), participanteId,
                DestinoVerdugo.ROCA_DIARIA, rocaDiariaId, CLOCK.now(), null, null, CLOCK.now(), CLOCK.now());
        adapter.save(pendiente);
        adapter.save(EventoVerdugo.registrar(participanteId, DestinoVerdugo.ROCA_DIARIA, rocaDiariaId,
                CLOCK.now(), ResultadoVerdugo.COMPLETADO, CLOCK));

        LocalDate hoy = LocalDate.of(2026, 8, 24);
        List<EventoVerdugo> pendientes = adapter.pendientesDeFecha(hoy);

        assertThat(pendientes).hasSize(1);
        assertThat(pendientes.get(0).pendiente()).isTrue();
    }
}
