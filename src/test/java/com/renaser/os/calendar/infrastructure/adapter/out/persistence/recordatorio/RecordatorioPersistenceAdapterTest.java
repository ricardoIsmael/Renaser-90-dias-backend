package com.renaser.os.calendar.infrastructure.adapter.out.persistence.recordatorio;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La cola de avisos. Lo que un mock no puede verificar y aca si: el
 * {@code INSERT ... ON CONFLICT DO NOTHING} contra el UNIQUE
 * {@code (evento_id, inicio_ocurrencia, usuario_id, enviar_en)} (idempotencia real del cron
 * de 5 minutos, que vuelve a pasar por los mismos avisos), el conteo de filas realmente
 * creadas que devuelve el batch de pgjdbc, y que cada cancelacion alcance exactamente a las
 * filas que debe.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RecordatorioPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-01T00:00:00Z"));
    private static final Instant INICIA_EN = Instant.parse("2026-09-05T19:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-09-05T18:00:00Z");
    private static final Instant VENCIDO = Instant.parse("2026-09-05T17:00:00Z");
    private static final Instant FUTURO = Instant.parse("2026-09-05T18:50:00Z");

    @Autowired
    private RecordatorioPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Test
    void encolarSiFaltaInsertaUnaVezYLaSegundaPasadaNoDuplica() {
        EventoId eventoId = crearEvento();
        UserId usuario = crearUsuario();
        List<RecordatorioEvento> lote = List.of(aviso(eventoId, usuario, VENCIDO), aviso(eventoId, usuario, FUTURO));

        assertThat(adapter.encolarSiFalta(lote)).isEqualTo(2);
        assertThat(adapter.encolarSiFalta(lote)).isZero();
        assertThat(colaDe(eventoId, FUTURO)).hasSize(2);
    }

    @Test
    void encolarSiFaltaNoConsultaConLoteVacio() {
        assertThat(adapter.encolarSiFalta(List.of())).isZero();
    }

    @Test
    void vencidosPendientesDejaAfueraLoQueTodaviaNoVence() {
        EventoId eventoId = crearEvento();
        UserId usuario = crearUsuario();
        adapter.encolarSiFalta(List.of(aviso(eventoId, usuario, VENCIDO), aviso(eventoId, usuario, FUTURO)));

        assertThat(colaDe(eventoId, AHORA)).extracting(RecordatorioEvento::enviarEn).containsExactly(VENCIDO);
    }

    @Test
    void marcarEnviadosSacaLaFilaDeLaCola() {
        EventoId eventoId = crearEvento();
        UserId usuario = crearUsuario();
        adapter.encolarSiFalta(List.of(aviso(eventoId, usuario, VENCIDO)));
        Long id = colaDe(eventoId, AHORA).get(0).id();

        adapter.marcarEnviados(List.of(id), AHORA);

        assertThat(colaDe(eventoId, AHORA)).isEmpty();
    }

    @Test
    void cancelarPorAsistenciaSoloAlcanzaAEsaPersonaEnEsaOcurrencia() {
        EventoId eventoId = crearEvento();
        UserId confirmo = crearUsuario();
        UserId otro = crearUsuario();
        adapter.encolarSiFalta(List.of(aviso(eventoId, confirmo, VENCIDO), aviso(eventoId, otro, VENCIDO)));

        int cancelados = adapter.cancelarPorAsistencia(confirmo, eventoId, INICIA_EN,
                RecordatorioEvento.MOTIVO_ASISTIRA);

        assertThat(cancelados).isEqualTo(1);
        assertThat(colaDe(eventoId, AHORA)).extracting(RecordatorioEvento::usuarioId).containsExactly(otro);
    }

    @Test
    void cancelarPorOcurrenciaAlcanzaATodosLosAvisosDeEsaOcurrencia() {
        EventoId eventoId = crearEvento();
        adapter.encolarSiFalta(List.of(aviso(eventoId, crearUsuario(), VENCIDO),
                aviso(eventoId, crearUsuario(), VENCIDO)));

        int cancelados = adapter.cancelarPorOcurrencia(eventoId, INICIA_EN,
                RecordatorioEvento.MOTIVO_EVENTO_CANCELADO);

        assertThat(cancelados).isEqualTo(2);
        assertThat(colaDe(eventoId, AHORA)).isEmpty();
    }

    @Test
    void borrarPendientesFuturosRespetaLoQueYaEstabaPorSalir() {
        EventoId eventoId = crearEvento();
        UserId usuario = crearUsuario();
        adapter.encolarSiFalta(List.of(aviso(eventoId, usuario, VENCIDO), aviso(eventoId, usuario, FUTURO)));

        int borrados = adapter.borrarPendientesFuturos(eventoId, AHORA);

        assertThat(borrados).isEqualTo(1);
        assertThat(colaDe(eventoId, FUTURO)).extracting(RecordatorioEvento::enviarEn).containsExactly(VENCIDO);
    }

    @Test
    void cancelarPorIdsNoConsultaConListaVacia() {
        assertThat(adapter.cancelarPorIds(List.of(), RecordatorioEvento.MOTIVO_NO_ELEGIBLE)).isZero();
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────────

    /** La base NO esta vacia: siempre se filtra por el evento de este test, nunca por indice. */
    private List<RecordatorioEvento> colaDe(EventoId eventoId, Instant hasta) {
        return adapter.vencidosPendientes(hasta, 100).stream()
                .filter(r -> r.eventoId().equals(eventoId))
                .toList();
    }

    private static RecordatorioEvento aviso(EventoId eventoId, UserId usuario, Instant enviarEn) {
        return RecordatorioEvento.programar(eventoId, INICIA_EN, usuario, enviarEn, CLOCK);
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
