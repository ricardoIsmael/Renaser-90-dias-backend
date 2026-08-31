package com.renaser.os.calendar.infrastructure.adapter.out.persistence.confirmacion;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.calendar.domain.model.confirmacion.Confirmacion;
import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PK compuesta {@code (evento_id, inicio_ocurrencia, usuario_id)} sin id propio (P-28 del
 * baseline): el upsert real solo se puede verificar contra Postgres — con un mock, "guardar
 * dos veces" siempre parece funcionar.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ConfirmacionPersistenceAdapterTest {

    private static final Instant INICIA_EN = Instant.parse("2026-09-05T19:00:00Z");
    private static final Instant CREADO_EN = Instant.parse("2026-09-01T00:00:00Z");

    @Autowired
    private ConfirmacionPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Test
    void paraVisorDevuelveElEstadoBajoLaClaveEventoOcurrencia() {
        EventoId eventoId = crearEvento();
        UserId usuario = crearUsuario();
        adapter.upsert(confirmacion(eventoId, usuario, EstadoConfirmacion.ASISTE));

        var porClave = adapter.paraVisor(usuario, Set.of(eventoId));

        assertThat(porClave).containsEntry(eventoId.value() + "|" + INICIA_EN, EstadoConfirmacion.ASISTE);
    }

    @Test
    void upsertSobreLaMismaClaveActualizaElEstadoYConservaCreadoEn() {
        EventoId eventoId = crearEvento();
        UserId usuario = crearUsuario();
        adapter.upsert(confirmacion(eventoId, usuario, EstadoConfirmacion.QUIZAS));

        Instant despues = CREADO_EN.plusSeconds(86_400);
        adapter.upsert(new Confirmacion(eventoId, INICIA_EN, usuario, EstadoConfirmacion.ASISTE, despues, despues));
        entityManager.flush();
        entityManager.clear();

        var fila = entityManager.find(ConfirmacionEventoJpaEntity.class,
                new ConfirmacionEventoId(eventoId.value(), INICIA_EN, usuario.value()));
        assertThat(fila.getEstado()).isEqualTo(EstadoConfirmacionJpa.ASISTE);
        assertThat(fila.getCreadoEn()).isEqualTo(CREADO_EN);
        assertThat(fila.getActualizadoEn()).isEqualTo(despues);
    }

    @Test
    void confirmadosAsistenciaDejaAfueraAQuienNoAsiste() {
        EventoId eventoId = crearEvento();
        UserId asiste = crearUsuario();
        UserId noAsiste = crearUsuario();
        adapter.upsert(confirmacion(eventoId, asiste, EstadoConfirmacion.ASISTE));
        adapter.upsert(confirmacion(eventoId, noAsiste, EstadoConfirmacion.NO_ASISTE));

        var confirmados = adapter.confirmadosAsistencia(eventoId, List.of(INICIA_EN));

        assertThat(confirmados).contains(INICIA_EN + "|" + asiste.value())
                .doesNotContain(INICIA_EN + "|" + noAsiste.value());
    }

    @Test
    void paraVisorNoConsultaCuandoNoHayEventos() {
        assertThat(adapter.paraVisor(UserId.of(UUID.randomUUID()), Set.of())).isEmpty();
    }

    @Test
    void confirmadosAsistenciaNoConsultaCuandoNoHayOcurrencias() {
        assertThat(adapter.confirmadosAsistencia(crearEvento(), List.of())).isEmpty();
    }

    private static Confirmacion confirmacion(EventoId eventoId, UserId usuario, EstadoConfirmacion estado) {
        return new Confirmacion(eventoId, INICIA_EN, usuario, estado, CREADO_EN, CREADO_EN);
    }

    private EventoId crearEvento() {
        EventoId id = EventoId.newId();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.eventos (id, titulo, inicia_en, duracion_minutos, timezone,
                                                     tipo_ubicacion, tipo_audiencia, tipo_evento, estado)
                        VALUES (:id, 'Evento fixture', TIMESTAMPTZ '2026-09-05 19:00:00+00', 60, 'America/Lima',
                                CAST('MEET' AS renaser.tipo_ubicacion),
                                CAST('TODOS' AS renaser.tipo_audiencia),
                                CAST('ESPONTANEO' AS renaser.tipo_evento_calendario),
                                CAST('PUBLICADO' AS renaser.estado_evento))
                        """)
                .setParameter("id", id.value())
                .executeUpdate();
        return id;
    }

    private UserId crearUsuario() {
        UserId id = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, :nombre, CAST('APRENDIZ' AS renaser.rol_usuario),
                                CAST('ACTIVO' AS renaser.estado_usuario))
                        """)
                .setParameter("id", id.value())
                .setParameter("email", id + "@renaser.test")
                .setParameter("nombre", "Fixture " + id)
                .executeUpdate();
        return id;
    }
}
