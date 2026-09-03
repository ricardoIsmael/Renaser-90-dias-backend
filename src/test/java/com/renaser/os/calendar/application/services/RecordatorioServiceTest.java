package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.api.RecordatorioEventoDebidoEvent;
import com.renaser.os.calendar.application.ports.out.celula.ConsultarMiembrosCelulaPort;
import com.renaser.os.calendar.application.ports.out.confirmacion.LoadConfirmacionPort;
import com.renaser.os.calendar.application.ports.out.curso.ResolverAudienciaCursoPort;
import com.renaser.os.calendar.application.ports.out.elegibilidad.ConsultarElegibilidadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadExcepcionPort;
import com.renaser.os.calendar.application.ports.out.nivelmembresia.LoadNivelMembresiaPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort;
import com.renaser.os.calendar.application.ports.out.participante.ResolverAudienciaMasivaPort;
import com.renaser.os.calendar.application.ports.out.recordatorio.LoadRecordatorioPort;
import com.renaser.os.calendar.application.ports.out.recordatorio.SaveRecordatorioPort;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordatorioServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-10T18:50:00Z"));

    @Mock
    private LoadEventoPort loadEventoPort;
    @Mock
    private LoadExcepcionPort loadExcepcionPort;
    @Mock
    private LoadConfirmacionPort loadConfirmacionPort;
    @Mock
    private LoadRecordatorioPort loadRecordatorioPort;
    @Mock
    private SaveRecordatorioPort saveRecordatorioPort;
    @Mock
    private LoadNivelMembresiaPort nivelPort;
    @Mock
    private ConsultarProgresoParticipanteCalendarPort progresoPort;
    @Mock
    private ResolverAudienciaMasivaPort audienciaMasivaPort;
    @Mock
    private ConsultarMiembrosCelulaPort celulaPort;
    @Mock
    private ResolverAudienciaCursoPort cursoPort;
    @Mock
    private ConsultarElegibilidadEventoPort elegibilidadPort;
    @Mock
    private ApplicationEventPublisher events;

    private RecordatorioService service;
    private final UserId usuarioId = UserId.of(UUID.randomUUID());
    private final EventoId eventoId = EventoId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new RecordatorioService(loadEventoPort, loadExcepcionPort, loadConfirmacionPort,
                loadRecordatorioPort, saveRecordatorioPort, nivelPort, progresoPort, audienciaMasivaPort, celulaPort,
                cursoPort, elegibilidadPort, events, CLOCK);
    }

    /** crear(), no rehydrate(): desde que el id entra por parametro (puerto IdGenerator), la
     * factoria real del agregado devuelve un Evento cuyo id() es EXACTAMENTE el que consulta el
     * mock. Antes habia que caer a rehydrate() — que ademas se saltea las validaciones y obliga a
     * repetir estado/creadoEn/actualizadoEn a mano — solo para poder fijar el id. */
    private Evento evento(TipoEvento tipo) {
        return Evento.crear(eventoId, "Sesion", null, Instant.parse("2026-09-10T19:00:00Z"), 60,
                ZoneId.of("America/Lima"), TipoUbicacion.MEET, "https://meet.google.com/abc", TipoAudiencia.TODOS,
                null, null, null, tipo, false, false, false, null, Set.of(), List.of(), usuarioId, CLOCK);
    }

    @Test
    void despacharPublicaUnEventoPorRecordatorioVencido() {
        RecordatorioEvento recordatorio = RecordatorioEvento.rehydrate(1L, eventoId, Instant.parse("2026-09-10T19:00:00Z"),
                usuarioId, Instant.parse("2026-09-10T18:50:00Z"), null, null, Instant.parse("2026-09-01T00:00:00Z"));
        when(loadRecordatorioPort.vencidosPendientes(any(), anyInt())).thenReturn(List.of(recordatorio));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(evento(TipoEvento.ESPONTANEO)));

        int despachados = service.despachar(CLOCK.now());

        assertThat(despachados).isEqualTo(1);
        ArgumentCaptor<RecordatorioEventoDebidoEvent> captor = ArgumentCaptor.forClass(RecordatorioEventoDebidoEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().eventoId()).isEqualTo(eventoId.value());
        assertThat(captor.getValue().destinatarioId()).isEqualTo(usuarioId);
        verify(saveRecordatorioPort).marcarEnviados(List.of(1L), CLOCK.now());
    }

    @Test
    void despacharCancelaEnVezDeEnviarSiElEventoFueCancelado() {
        RecordatorioEvento recordatorio = RecordatorioEvento.rehydrate(1L, eventoId, Instant.parse("2026-09-10T19:00:00Z"),
                usuarioId, Instant.parse("2026-09-10T18:50:00Z"), null, null, Instant.parse("2026-09-01T00:00:00Z"));
        when(loadRecordatorioPort.vencidosPendientes(any(), anyInt())).thenReturn(List.of(recordatorio));
        Evento eventoCancelado = evento(TipoEvento.ESPONTANEO);
        eventoCancelado.cancelar(CLOCK);
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(eventoCancelado));

        int despachados = service.despachar(CLOCK.now());

        assertThat(despachados).isZero();
        verify(events, never()).publishEvent(any());
        verify(saveRecordatorioPort).cancelarPorIds(List.of(1L), RecordatorioEvento.MOTIVO_EVENTO_CANCELADO);
    }

    @Test
    void generarNoHaceNadaSiNoHayEventosCandidatos() {
        when(loadEventoPort.candidatosParaRecordatorios(any(), any(), any())).thenReturn(List.of());

        int creados = service.generar(CLOCK.now());

        assertThat(creados).isZero();
        verify(saveRecordatorioPort, never()).encolarSiFalta(anyList());
    }
}
