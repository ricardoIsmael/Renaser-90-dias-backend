package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.api.RecordatorioEventoDebidoEvent;
import com.renaser.os.calendar.application.ports.out.confirmacion.LoadConfirmacionPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadExcepcionPort;
import com.renaser.os.calendar.application.ports.out.recordatorio.LoadRecordatorioPort;
import com.renaser.os.calendar.application.ports.out.recordatorio.SaveRecordatorioPort;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.FrecuenciaRecurrencia;
import com.renaser.os.calendar.domain.model.evento.Recurrencia;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El despacho de la cola de recordatorios. Las cuatro primeras pruebas vienen de {@code RecordatorioServiceTest}
 * (el despacho vivia ahi hasta el 2026-09-27); las de lotes y anuncio son de E-360 y E-361.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DespachoDeRecordatoriosServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-10T18:50:00Z"));

    @Mock
    private LoadRecordatorioPort loadRecordatorioPort;
    @Mock
    private SaveRecordatorioPort saveRecordatorioPort;
    @Mock
    private LoadEventoPort loadEventoPort;
    @Mock
    private LoadExcepcionPort loadExcepcionPort;
    @Mock
    private LoadConfirmacionPort loadConfirmacionPort;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private PlatformTransactionManager transactionManager;

    private DespachoDeRecordatoriosService service;
    private final UserId usuarioId = UserId.of(UUID.randomUUID());
    private final EventoId eventoId = EventoId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new DespachoDeRecordatoriosService(loadRecordatorioPort, saveRecordatorioPort, loadEventoPort,
                loadExcepcionPort, loadConfirmacionPort, events, transactionManager);
        when(loadRecordatorioPort.vencidosPendientes(any(), anyInt())).thenReturn(List.of());
    }

    // ── Heredadas de RecordatorioServiceTest ────────────────────────────────

    @Test
    void despacharPublicaUnEventoPorRecordatorioVencido() {
        RecordatorioEvento recordatorio = recordatorio(1L, Instant.parse("2026-09-10T19:00:00Z"),
                Instant.parse("2026-09-10T18:50:00Z"));
        when(loadRecordatorioPort.vencidosPendientes(any(), anyInt())).thenReturn(List.of(recordatorio));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(eventoCreadoDiasAntes()));

        int despachados = service.despachar(CLOCK.now());

        assertThat(despachados).isEqualTo(1);
        RecordatorioEventoDebidoEvent publicado = unicoPublicado();
        assertThat(publicado.eventoId()).isEqualTo(eventoId.value());
        assertThat(publicado.destinatarioId()).isEqualTo(usuarioId);
        assertThat(publicado.esAnuncio()).isFalse();
        assertThat(publicado.confirmoAsistencia()).isFalse();
        verify(saveRecordatorioPort).marcarEnviados(List.of(1L), CLOCK.now());
    }

    @Test
    void despacharCancelaEnVezDeEnviarSiElEventoFueCancelado() {
        RecordatorioEvento recordatorio = recordatorio(1L, Instant.parse("2026-09-10T19:00:00Z"),
                Instant.parse("2026-09-10T18:50:00Z"));
        when(loadRecordatorioPort.vencidosPendientes(any(), anyInt())).thenReturn(List.of(recordatorio));
        Evento eventoCancelado = eventoCreadoDiasAntes();
        eventoCancelado.cancelar(CLOCK);
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(eventoCancelado));

        int despachados = service.despachar(CLOCK.now());

        assertThat(despachados).isZero();
        verify(events, never()).publishEvent(any());
        verify(saveRecordatorioPort).cancelarPorIds(List.of(1L), RecordatorioEvento.MOTIVO_EVENTO_CANCELADO);
        verify(saveRecordatorioPort, never()).marcarEnviados(anyList(), any());
    }

    @Test
    @DisplayName("D-189: al despachar, el evento dice si la persona dijo Voy a esa ocurrencia (leido en ese momento)")
    void despacharAvisaQueLaPersonaDijoVoy() {
        RecordatorioEvento recordatorio = recordatorio(1L, Instant.parse("2026-09-10T19:00:00Z"),
                Instant.parse("2026-09-10T18:50:00Z"));
        when(loadRecordatorioPort.vencidosPendientes(any(), anyInt())).thenReturn(List.of(recordatorio));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(eventoCreadoDiasAntes()));
        when(loadConfirmacionPort.confirmadosAsistencia(eventoId, List.of(recordatorio.inicioOcurrencia())))
                .thenReturn(Set.of(recordatorio.inicioOcurrencia() + "|" + usuarioId));

        service.despachar(CLOCK.now());

        assertThat(unicoPublicado().confirmoAsistencia()).isTrue();
        verify(saveRecordatorioPort).marcarEnviados(List.of(1L), CLOCK.now());
    }

    @Test
    @DisplayName("D-189: el Voy de OTRA persona no marca el recordatorio de esta")
    void despacharNoMezclaLaConfirmacionDeOtraPersona() {
        RecordatorioEvento recordatorio = recordatorio(1L, Instant.parse("2026-09-10T19:00:00Z"),
                Instant.parse("2026-09-10T18:50:00Z"));
        when(loadRecordatorioPort.vencidosPendientes(any(), anyInt())).thenReturn(List.of(recordatorio));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(eventoCreadoDiasAntes()));
        when(loadConfirmacionPort.confirmadosAsistencia(any(), anyList()))
                .thenReturn(Set.of(recordatorio.inicioOcurrencia() + "|" + UUID.randomUUID()));

        service.despachar(CLOCK.now());

        assertThat(unicoPublicado().confirmoAsistencia()).isFalse();
    }

    // ── E-360: de a lotes ───────────────────────────────────────────────────

    /**
     * Contra el codigo anterior falla de dos maneras: una pasada tomaba hasta 500 filas y paraba (la
     * segunda tanda esperaba al minuto siguiente), y leia el evento una vez por FILA.
     */
    @Test
    @DisplayName("E-360: un lote lleno sigue con el siguiente en la misma pasada, cada uno en su transaccion, "
            + "y el evento se lee una vez por lote")
    void unaPasadaVaciaLaColaDeALotes() {
        List<RecordatorioEvento> primerLote = recordatorios(1, DespachoDeRecordatoriosService.TAMANO_LOTE);
        List<RecordatorioEvento> segundoLote = recordatorios(DespachoDeRecordatoriosService.TAMANO_LOTE + 1, 7);
        when(loadRecordatorioPort.vencidosPendientes(any(), anyInt())).thenReturn(primerLote, segundoLote);
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(eventoCreadoDiasAntes()));

        int despachados = service.despachar(CLOCK.now());

        assertThat(despachados).isEqualTo(DespachoDeRecordatoriosService.TAMANO_LOTE + 7);
        verify(loadRecordatorioPort, times(2)).vencidosPendientes(CLOCK.now(), DespachoDeRecordatoriosService.TAMANO_LOTE);
        verify(transactionManager, times(2)).getTransaction(any());
        verify(loadEventoPort, times(2)).byId(eventoId);
        verify(saveRecordatorioPort).marcarEnviados(ids(primerLote), CLOCK.now());
        verify(saveRecordatorioPort).marcarEnviados(ids(segundoLote), CLOCK.now());
    }

    /**
     * Marcar es un UPDATE que vacia el contexto de persistencia; si viene despues de publicar, se lleva las
     * publicaciones del outbox sin escribirlas ({@code AvisosDeEventoEnMasaIT} lo prueba contra la base).
     * Contra el codigo anterior falla: publicaba y despues marcaba.
     */
    @Test
    @DisplayName("E-360: primero se marca la fila enviada (y las canceladas), despues se publica el aviso")
    void primeroSeMarcaDespuesSePublica() {
        when(loadRecordatorioPort.vencidosPendientes(any(), anyInt())).thenReturn(List.of(recordatorio(1L,
                Instant.parse("2026-09-10T19:00:00Z"), Instant.parse("2026-09-10T18:50:00Z"))));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(eventoCreadoDiasAntes()));

        service.despachar(CLOCK.now());

        InOrder orden = inOrder(saveRecordatorioPort, events);
        orden.verify(saveRecordatorioPort).marcarEnviados(List.of(1L), CLOCK.now());
        orden.verify(events).publishEvent(any(RecordatorioEventoDebidoEvent.class));
    }

    @Test
    @DisplayName("E-360: la pasada tiene tope; lo que quede sale en la del minuto siguiente")
    void unaPasadaNoSigueParaSiempre() {
        when(loadRecordatorioPort.vencidosPendientes(any(), anyInt()))
                .thenReturn(recordatorios(1, DespachoDeRecordatoriosService.TAMANO_LOTE));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(eventoCreadoDiasAntes()));

        service.despachar(CLOCK.now());

        verify(loadRecordatorioPort, times(DespachoDeRecordatoriosService.MAXIMO_LOTES_POR_PASADA))
                .vencidosPendientes(any(), anyInt());
    }

    // ── E-361: el anuncio dice cuando es el evento ──────────────────────────

    /**
     * El caso del 2026-09-27: el evento se creo el sabado 26 a las 20:02 de Lima (01:02 UTC del 27) para el
     * domingo 27 a las 19:00 de Lima (00:00 UTC del 28), y el anuncio sale a las 03:00 UTC del 27, que en
     * Lima todavia es el sabado 26 (regla 03). Antes viajaba la hora de creacion y el aviso decia «Es el
     * sabado 26 de setiembre a las 20:02».
     */
    @Test
    @DisplayName("E-361: el anuncio de un evento nuevo lleva el inicio del evento, no la hora en que se creo")
    void elAnuncioLlevaElInicioDelEvento() {
        Instant creado = Instant.parse("2026-09-27T01:02:31Z");
        Instant inicio = Instant.parse("2026-09-28T00:00:00Z");
        Instant ahora = Instant.parse("2026-09-27T03:00:00Z");
        when(loadRecordatorioPort.vencidosPendientes(any(), anyInt()))
                .thenReturn(List.of(recordatorio(9L, creado, creado)));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(evento(inicio, null, creado)));

        service.despachar(ahora);

        RecordatorioEventoDebidoEvent anuncio = unicoPublicado();
        assertThat(anuncio.esAnuncio()).isTrue();
        assertThat(anuncio.inicioOcurrencia()).isEqualTo(inicio);
        assertThat(anuncio.zonaHoraria()).isEqualTo("America/Lima");
    }

    @Test
    @DisplayName("E-361: el anuncio de una serie que ya habia empezado lleva su proxima ocurrencia")
    void elAnuncioDeUnaSerieLlevaLaProximaOcurrencia() {
        Instant primeraClase = Instant.parse("2026-09-01T00:00:00Z");  // lunes 31/08 19:00 en Lima
        Instant creado = Instant.parse("2026-09-27T01:02:31Z");
        Instant ahora = Instant.parse("2026-09-27T03:00:00Z");         // sabado 26 22:00 en Lima
        Recurrencia semanal = new Recurrencia(FrecuenciaRecurrencia.SEMANAL, 1, null, null, Set.of(DayOfWeek.MONDAY));
        when(loadRecordatorioPort.vencidosPendientes(any(), anyInt()))
                .thenReturn(List.of(recordatorio(9L, creado, creado)));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(evento(primeraClase, semanal, creado)));
        when(loadExcepcionPort.porEvento(eventoId)).thenReturn(List.of());

        service.despachar(ahora);

        // Lunes 28/09 19:00 de Lima = 00:00 UTC del martes 29.
        assertThat(unicoPublicado().inicioOcurrencia()).isEqualTo(Instant.parse("2026-09-29T00:00:00Z"));
    }

    // ── Escenario ───────────────────────────────────────────────────────────

    private RecordatorioEvento recordatorio(long id, Instant inicioOcurrencia, Instant enviarEn) {
        return RecordatorioEvento.rehydrate(id, eventoId, inicioOcurrencia, usuarioId, enviarEn, null, null,
                Instant.parse("2026-09-01T00:00:00Z"));
    }

    private List<RecordatorioEvento> recordatorios(long desde, int cuantos) {
        List<RecordatorioEvento> filas = new ArrayList<>();
        LongStream.range(desde, desde + cuantos).forEach(id -> filas.add(RecordatorioEvento.rehydrate(id, eventoId,
                Instant.parse("2026-09-10T19:00:00Z"), UserId.of(UUID.randomUUID()),
                Instant.parse("2026-09-10T18:50:00Z"), null, null, Instant.parse("2026-09-01T00:00:00Z"))));
        return filas;
    }

    private static List<Long> ids(List<RecordatorioEvento> filas) {
        return filas.stream().map(RecordatorioEvento::id).toList();
    }

    /** Creado dias antes del recordatorio: asi un aviso de las 18:50 no cuenta como anuncio. */
    private Evento eventoCreadoDiasAntes() {
        return evento(Instant.parse("2026-09-10T19:00:00Z"), null, Instant.parse("2026-09-01T00:00:00Z"));
    }

    private Evento evento(Instant inicio, Recurrencia recurrencia, Instant creadoEn) {
        return Evento.crear(eventoId, "Clase desde la app", null, inicio, 60, LIMA, TipoUbicacion.MEET,
                "https://meet.google.com/abc", TipoAudiencia.TODOS, null, null, null, TipoEvento.SESION_ESPECIAL,
                true, false, false, recurrencia, Set.of(), List.of(), usuarioId, FixedClock.at(creadoEn));
    }

    private RecordatorioEventoDebidoEvent unicoPublicado() {
        ArgumentCaptor<RecordatorioEventoDebidoEvent> captor = ArgumentCaptor.forClass(RecordatorioEventoDebidoEvent.class);
        verify(events).publishEvent(captor.capture());
        return captor.getValue();
    }
}
