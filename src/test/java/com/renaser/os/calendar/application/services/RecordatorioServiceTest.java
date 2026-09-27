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
import com.renaser.os.calendar.domain.model.evento.ReglaRecordatorio;
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
import static org.mockito.Mockito.lenient;
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
        assertThat(captor.getValue().confirmoAsistencia()).isFalse();
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

    /**
     * Evento creado dias antes del recordatorio. Con {@link #evento} (creado a la misma hora del
     * reloj, 18:50) un aviso de las 18:50 cuenta como ANUNCIO ({@code esAnuncio}: enviarEn no
     * posterior a la creacion), y un anuncio nunca se trata como cubierto por el "Voy".
     */
    private Evento eventoCreadoDiasAntes() {
        return Evento.crear(eventoId, "Sesion", null, Instant.parse("2026-09-10T19:00:00Z"), 60,
                ZoneId.of("America/Lima"), TipoUbicacion.MEET, "https://meet.google.com/abc", TipoAudiencia.TODOS,
                null, null, null, TipoEvento.ESPONTANEO, false, false, false, null, Set.of(), List.of(), usuarioId,
                FixedClock.at(Instant.parse("2026-09-01T00:00:00Z")));
    }

    /** Ocurrencia a las 01:00 UTC del 11 = 20:00 del 10 en Lima: la fecha UTC y la local difieren (regla 03). */
    private static final Instant OCURRENCIA_NOCHE = Instant.parse("2026-09-11T01:00:00Z");

    /**
     * D-189: antes {@code generar()} dejaba afuera de la cola a quien ya habia dicho "Voy" (y
     * {@code confirmar()} cancelaba lo ya encolado). Quien respondia desde la web, sin alarma
     * local, no recibia ningun aviso. Ahora se encola igual; si el telefono lo cubre lo decide
     * {@code notifications} al entregar. Contra el codigo viejo esta prueba falla: no se encola nada.
     */
    @Test
    @DisplayName("D-189: generar encola los avisos aunque la persona ya haya dicho Voy")
    void generarEncolaAunqueLaPersonaYaDijoVoy() {
        Evento evento = Evento.crear(eventoId, "Sesion", null, OCURRENCIA_NOCHE, 60, ZoneId.of("America/Lima"),
                TipoUbicacion.MEET, "https://meet.google.com/abc", TipoAudiencia.TODOS, null, null, null,
                TipoEvento.ESPONTANEO, false, false, true, null, Set.of(),
                List.of(ReglaRecordatorio.minutosAntes(1, 30)), usuarioId, CLOCK);
        when(loadEventoPort.candidatosParaRecordatorios(any(), any(), any())).thenReturn(List.of(evento));
        when(audienciaMasivaPort.traineesActivos()).thenReturn(List.of(usuarioId));
        lenient().when(loadConfirmacionPort.confirmadosAsistencia(any(), anyList()))
                .thenReturn(Set.of(OCURRENCIA_NOCHE + "|" + usuarioId));
        when(saveRecordatorioPort.encolarSiFalta(anyList())).thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());

        int creados = service.generar(CLOCK.now());

        assertThat(creados).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecordatorioEvento>> filas = ArgumentCaptor.forClass(List.class);
        verify(saveRecordatorioPort).encolarSiFalta(filas.capture());
        assertThat(filas.getValue()).extracting(RecordatorioEvento::usuarioId).containsExactly(usuarioId);
    }

    @Test
    @DisplayName("D-189: al despachar, el evento dice si la persona dijo Voy a esa ocurrencia (leido en ese momento)")
    void despacharAvisaQueLaPersonaDijoVoy() {
        RecordatorioEvento recordatorio = RecordatorioEvento.rehydrate(1L, eventoId, Instant.parse("2026-09-10T19:00:00Z"),
                usuarioId, Instant.parse("2026-09-10T18:50:00Z"), null, null, Instant.parse("2026-09-01T00:00:00Z"));
        when(loadRecordatorioPort.vencidosPendientes(any(), anyInt())).thenReturn(List.of(recordatorio));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(eventoCreadoDiasAntes()));
        when(loadConfirmacionPort.confirmadosAsistencia(eventoId, List.of(recordatorio.inicioOcurrencia())))
                .thenReturn(Set.of(recordatorio.inicioOcurrencia() + "|" + usuarioId));

        service.despachar(CLOCK.now());

        ArgumentCaptor<RecordatorioEventoDebidoEvent> captor = ArgumentCaptor.forClass(RecordatorioEventoDebidoEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().confirmoAsistencia()).isTrue();
        verify(saveRecordatorioPort).marcarEnviados(List.of(1L), CLOCK.now());
    }

    @Test
    @DisplayName("D-189: el Voy de OTRA persona no marca el recordatorio de esta")
    void despacharNoMezclaLaConfirmacionDeOtraPersona() {
        RecordatorioEvento recordatorio = RecordatorioEvento.rehydrate(1L, eventoId, Instant.parse("2026-09-10T19:00:00Z"),
                usuarioId, Instant.parse("2026-09-10T18:50:00Z"), null, null, Instant.parse("2026-09-01T00:00:00Z"));
        when(loadRecordatorioPort.vencidosPendientes(any(), anyInt())).thenReturn(List.of(recordatorio));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(eventoCreadoDiasAntes()));
        when(loadConfirmacionPort.confirmadosAsistencia(any(), anyList()))
                .thenReturn(Set.of(recordatorio.inicioOcurrencia() + "|" + UUID.randomUUID()));

        service.despachar(CLOCK.now());

        ArgumentCaptor<RecordatorioEventoDebidoEvent> captor = ArgumentCaptor.forClass(RecordatorioEventoDebidoEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().confirmoAsistencia()).isFalse();
    }
}
