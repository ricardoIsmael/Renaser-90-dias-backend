package com.renaser.os.calendar.infrastructure.adapter.out.persistence.evento;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.Excepcion;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que solo se ve contra Postgres: el UNIQUE {@code (evento_id, inicio_ocurrencia)} y el
 * upsert que lo respeta — reusar el id existente en vez de intentar una segunda fila — mas
 * el {@code saveAndFlush} que el javadoc del adaptador documenta como imprescindible
 * (cancelar una ocurrencia respondia 200 sin persistir nada).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ExcepcionPersistenceAdapterTest {

    private static final Instant INICIA_EN = Instant.parse("2026-09-05T19:00:00Z");

    @Autowired
    private ExcepcionPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Test
    void upsertGuardaLaExcepcionYPorEventoLaDevuelve() {
        EventoId eventoId = crearEvento();

        Excepcion guardada = adapter.upsert(Excepcion.cancelar(UUID.randomUUID(), eventoId, INICIA_EN));

        assertThat(guardada.cancelada()).isTrue();
        assertThat(adapter.porEvento(eventoId)).singleElement()
                .satisfies(e -> assertThat(e.inicioOcurrencia()).isEqualTo(INICIA_EN));
    }

    @Test
    void upsertSobreLaMismaOcurrenciaReemplazaLaFilaEnVezDeDuplicarla() {
        EventoId eventoId = crearEvento();
        Excepcion cancelada = adapter.upsert(Excepcion.cancelar(UUID.randomUUID(), eventoId, INICIA_EN));

        Excepcion reprogramada = adapter.upsert(new Excepcion(cancelada.id(), eventoId, INICIA_EN, false,
                INICIA_EN.plusSeconds(3600), 45, "Titulo nuevo"));
        entityManager.flush();

        assertThat(reprogramada.id()).isEqualTo(cancelada.id());
        assertThat(adapter.porEvento(eventoId)).singleElement().satisfies(e -> {
            assertThat(e.cancelada()).isFalse();
            assertThat(e.nuevoInicio()).isEqualTo(INICIA_EN.plusSeconds(3600));
            assertThat(e.nuevaDuracion()).isEqualTo(45);
            assertThat(e.nuevoTitulo()).isEqualTo("Titulo nuevo");
        });
    }

    /** upsert con un id NUEVO sobre una ocurrencia ya excepcionada: el adaptador debe
     * reutilizar el id que ya esta en la base, no violar el UNIQUE. */
    @Test
    void upsertConIdNuevoSobreUnaOcurrenciaYaExcepcionadaNoRompeElUnique() {
        EventoId eventoId = crearEvento();
        Excepcion primera = adapter.upsert(Excepcion.cancelar(UUID.randomUUID(), eventoId, INICIA_EN));

        Excepcion segunda = adapter.upsert(Excepcion.cancelar(UUID.randomUUID(), eventoId, INICIA_EN));
        entityManager.flush();

        assertThat(segunda.id()).isEqualTo(primera.id());
        assertThat(adapter.porEvento(eventoId)).hasSize(1);
    }

    @Test
    void porEventosAgrupaLasExcepcionesPorSuEvento() {
        EventoId unEvento = crearEvento();
        EventoId otroEvento = crearEvento();
        adapter.upsert(Excepcion.cancelar(UUID.randomUUID(), unEvento, INICIA_EN));
        adapter.upsert(Excepcion.cancelar(UUID.randomUUID(), otroEvento, INICIA_EN.plusSeconds(86_400)));

        var porEvento = adapter.porEventos(Set.of(unEvento, otroEvento));

        assertThat(porEvento).containsOnlyKeys(unEvento, otroEvento);
        assertThat(porEvento.get(unEvento)).hasSize(1);
        assertThat(porEvento.get(otroEvento)).hasSize(1);
    }

    @Test
    void porEventosNoConsultaCuandoNoHayEventos() {
        assertThat(adapter.porEventos(Set.of())).isEmpty();
    }

    @Test
    void porEventoDevuelveVacioCuandoElEventoNoTieneExcepciones() {
        assertThat(adapter.porEvento(crearEvento())).isEmpty();
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
}
