package com.renaser.os.calendar.infrastructure.adapter.out.persistence.recordatorio;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #86: la cola con el driver como en produccion, que reescribe un lote de INSERT en UN solo {@code INSERT ... VALUES (...),(...)}
 * ({@code reWriteBatchedInserts}, application.yaml de main). El application.yaml de las pruebas no lo trae, y por eso
 * {@link RecordatorioPersistenceAdapterTest} no veia el choque: en sentencias separadas el segundo {@code ON CONFLICT} junta contra la
 * fila que dejo el primero. En una sola, Postgres responde «ON CONFLICT DO UPDATE command cannot affect row a second time».
 */
@SpringBootTest(properties = "spring.datasource.hikari.data-source-properties.reWriteBatchedInserts=true")
@Import(TestcontainersConfiguration.class)
@Transactional
class RecordatorioPersistenceAdapterLoteReescritoTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-01T00:00:00Z"));
    private static final Instant INICIA_EN = Instant.parse("2026-09-05T19:00:00Z");
    private static final Instant DIEZ_ANTES = Instant.parse("2026-09-05T18:50:00Z");
    private static final Instant TREINTA_ANTES = Instant.parse("2026-09-05T18:30:00Z");

    @Autowired
    private RecordatorioPersistenceAdapter adapter;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("la prueba corre con el driver reescribiendo los lotes, como en produccion")
    void elDriverReescribeLosLotes() throws java.sql.SQLException {
        assertThat(dataSource.unwrap(HikariDataSource.class).getDataSourceProperties())
                .containsEntry("reWriteBatchedInserts", "true");
    }

    // spec 041 FR-014
    @Test
    @DisplayName("issue #86: el aviso del telefono y el de mensaje del MISMO minuto, en el mismo lote, son UNA fila con las dos marcas")
    void elMismoMinutoEnElMismoLoteEsUnaFila() {
        EventoId eventoId = crearEvento();
        UserId primero = crearUsuario();
        UserId segundo = crearUsuario();
        List<RecordatorioEvento> lote = List.of(
                RecordatorioEvento.programar(eventoId, INICIA_EN, primero, DIEZ_ANTES, CLOCK),
                RecordatorioEvento.programar(eventoId, INICIA_EN, segundo, DIEZ_ANTES, CLOCK),
                RecordatorioEvento.programarMensaje(eventoId, INICIA_EN, primero, TREINTA_ANTES, 30, CLOCK),
                RecordatorioEvento.programarMensaje(eventoId, INICIA_EN, primero, DIEZ_ANTES, 10, CLOCK),
                RecordatorioEvento.programarMensaje(eventoId, INICIA_EN, segundo, DIEZ_ANTES, 10, CLOCK));

        adapter.encolarSiFalta(lote);
        adapter.encolarSiFalta(lote);

        // (Con el lote reescrito el driver no informa cuantas filas creo cada una: se cuentan en la tabla.)
        List<RecordatorioEvento> cola = adapter.vencidosPendientes(DIEZ_ANTES, 100).stream()
                .filter(r -> r.eventoId().equals(eventoId)).toList();
        assertThat(cola).as("dos de las 18:50 y una de las 18:30, tambien despues de pasar dos veces").hasSize(3);
        assertThat(cola).filteredOn(r -> r.enviarEn().equals(DIEZ_ANTES)).hasSize(2).allSatisfy(fila -> {
            assertThat(fila.avisoEnApp()).as("es aviso del telefono").isTrue();
            assertThat(fila.mensajeMinAntes()).as("y tambien de mensaje").isEqualTo(10);
        });
    }

    private EventoId crearEvento() {
        EventoId id = EventoId.of(UUID.randomUUID());
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
