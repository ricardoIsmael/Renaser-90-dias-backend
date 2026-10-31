package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D-293, el recordatorio YA generado: se evalua al despacharlo con el semaforo de ese momento. Aca se prueba que
 * decide el servicio que cuida la cola ({@link ElegibilidadDeLosAvisos}); el efecto sobre la fila (cancelada con
 * {@code dejo_de_ser_elegible}, no publicada) esta en {@code DespachoDeRecordatoriosServiceTest} y contra la base
 * en {@code MentoriaConSemaforoVerdeIT}.
 */
@ExtendWith(MockitoExtension.class)
class ElegibilidadDeLosAvisosTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-30T15:00:00Z"));
    private static final Instant INICIO = Instant.parse("2026-11-04T01:00:00Z");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Mock
    private AudienciaDelEventoService audiencia;

    private ElegibilidadDeLosAvisos elegibilidad;
    private final Map<EventoId, Optional<Evento>> eventos = new HashMap<>();
    private final UserId verde = UserId.of(UUID.randomUUID());
    private final UserId amarillo = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        elegibilidad = new ElegibilidadDeLosAvisos(audiencia);
    }

    @Test
    @DisplayName("D-293: el aviso de quien ya no esta en verde se marca; el de quien sigue en verde no")
    void marcaSoloAlQueDejoDeSerElegible() {
        Evento mentoria = evento(TipoEvento.MENTORIA_ALQUIMISTA);
        RecordatorioEvento delVerde = recordatorio(1L, mentoria, verde);
        RecordatorioEvento delAmarillo = recordatorio(2L, mentoria, amarillo);
        when(audiencia.quienesPasan(anyCollection(), eq(TipoEvento.MENTORIA_ALQUIMISTA))).thenReturn(Set.of(verde));

        Set<Long> ids = elegibilidad.dejaronDeSerElegibles(List.of(delVerde, delAmarillo), eventos);

        assertThat(ids).containsExactly(2L);
    }

    @Test
    @DisplayName("D-293: se pregunta una vez por tipo con todas las personas del lote, sin repetir a nadie")
    void unaConsultaPorTipo() {
        Evento mentoria = evento(TipoEvento.MENTORIA_ALQUIMISTA);
        Evento otraMentoria = evento(TipoEvento.MENTORIA_ALQUIMISTA);
        when(audiencia.quienesPasan(anyCollection(), eq(TipoEvento.MENTORIA_ALQUIMISTA)))
                .thenReturn(Set.of(verde, amarillo));

        elegibilidad.dejaronDeSerElegibles(List.of(recordatorio(1L, mentoria, verde),
                recordatorio(2L, mentoria, amarillo), recordatorio(3L, otraMentoria, amarillo)), eventos);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.Collection<UserId>> personas = org.mockito.ArgumentCaptor
                .forClass(java.util.Collection.class);
        verify(audiencia, times(1)).quienesPasan(personas.capture(), eq(TipoEvento.MENTORIA_ALQUIMISTA));
        assertThat(personas.getValue()).containsExactlyInAnyOrder(verde, amarillo);
    }

    @Test
    @DisplayName("D-293: los eventos de otros tipos no se preguntan ni se tocan")
    void losDemasTiposNoSePreguntan() {
        Evento espontaneo = evento(TipoEvento.ESPONTANEO);

        Set<Long> ids = elegibilidad.dejaronDeSerElegibles(List.of(recordatorio(1L, espontaneo, amarillo)), eventos);

        assertThat(ids).isEmpty();
        verify(audiencia, never()).quienesPasan(anyCollection(), any());
    }

    @Test
    @DisplayName("D-293: un evento cancelado no se pregunta: ya lo cancela el despacho con su propio motivo")
    void unEventoCanceladoNoSePregunta() {
        Evento cancelado = evento(TipoEvento.MENTORIA_ALQUIMISTA);
        cancelado.cancelar(CLOCK);

        Set<Long> ids = elegibilidad.dejaronDeSerElegibles(List.of(recordatorio(1L, cancelado, amarillo)), eventos);

        assertThat(ids).isEmpty();
        verify(audiencia, never()).quienesPasan(anyCollection(), any());
    }

    @Test
    @DisplayName("D-293: un evento que ya no existe no rompe el lote")
    void unEventoQueNoExisteSeIgnora() {
        RecordatorioEvento huerfano = RecordatorioEvento.rehydrate(1L, EventoId.of(UUID.randomUUID()), INICIO, amarillo,
                INICIO.minusSeconds(600), null, null, CLOCK.now());

        assertThat(elegibilidad.dejaronDeSerElegibles(List.of(huerfano), eventos)).isEmpty();
    }

    // ── Escenario ───────────────────────────────────────────────────────────

    private Evento evento(TipoEvento tipo) {
        Evento evento = Evento.crear(EventoId.of(UUID.randomUUID()), "Evento", null, INICIO, 60, LIMA,
                TipoUbicacion.MEET, "https://meet.google.com/abc-defg-hij", TipoAudiencia.TODOS, null, null, null, tipo,
                true, false, false, null, Set.of(), List.of(), verde, CLOCK);
        eventos.put(evento.id(), Optional.of(evento));
        return evento;
    }

    private RecordatorioEvento recordatorio(long id, Evento evento, UserId persona) {
        return RecordatorioEvento.rehydrate(id, evento.id(), INICIO, persona, INICIO.minusSeconds(600), null, null,
                CLOCK.now());
    }
}
