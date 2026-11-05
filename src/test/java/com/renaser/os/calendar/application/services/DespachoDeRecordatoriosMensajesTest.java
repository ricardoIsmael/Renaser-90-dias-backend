package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.api.RecordatorioEventoDebidoEvent;
import com.renaser.os.calendar.application.ports.out.confirmacion.LoadConfirmacionPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadExcepcionPort;
import com.renaser.os.calendar.application.ports.out.recordatorio.LoadRecordatorioPort;
import com.renaser.os.calendar.application.ports.out.recordatorio.SaveRecordatorioPort;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que el despacho manda a {@code notifications} para los avisos de MENSAJE (spec 041, FR-014 y FR-031): que aviso es, a que formacion pertenece el evento y
 * donde es (enlace o direccion), leido del evento AL DESPACHAR, no al programar.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DespachoDeRecordatoriosMensajesTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-10T18:50:00Z"));
    private static final Instant INICIO = Instant.parse("2026-09-11T00:00:00Z");

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
    private ElegibilidadDeLosAvisos elegibilidad;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private PlatformTransactionManager transactionManager;

    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private final EventoId eventoId = EventoId.of(UUID.randomUUID());
    private DespachoDeRecordatoriosService service;

    @BeforeEach
    void setUp() {
        service = new DespachoDeRecordatoriosService(loadRecordatorioPort, saveRecordatorioPort, loadEventoPort, loadExcepcionPort,
                loadConfirmacionPort, elegibilidad, events, transactionManager);
    }

    // spec 041 FR-014, FR-031
    @Test
    @DisplayName("un aviso de mensaje viaja con sus minutos, la formacion del evento y el enlace para entrar")
    void elAvisoDeMensajeLlevaLoQueHaceFalta() {
        when(loadRecordatorioPort.vencidosPendientes(any(), anyInt())).thenReturn(List.of(fila(true, 10)));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(evento(Formacion.DIPLOMADO, TipoUbicacion.MEET, "https://meet.google.com/abc-defg-hij")));

        service.despachar(CLOCK.now());

        RecordatorioEventoDebidoEvent publicado = unicoPublicado();
        assertThat(publicado.esAvisoDeMensaje()).isTrue();
        assertThat(publicado.avisoDeMensajeMin()).isEqualTo(10);
        assertThat(publicado.formacion()).isEqualTo("DIPLOMADO");
        assertThat(publicado.enlace()).isEqualTo("https://meet.google.com/abc-defg-hij");
        assertThat(publicado.lugar()).isNull();
    }

    // spec 041 FR-001, FR-017
    @Test
    @DisplayName("un evento presencial manda su direccion, no un enlace")
    void unEventoPresencialMandaSuDireccion() {
        when(loadRecordatorioPort.vencidosPendientes(any(), anyInt())).thenReturn(List.of(fila(true, 30)));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(evento(Formacion.ESCUELA, TipoUbicacion.DIRECCION, "Av. Ejercito 123, Arequipa")));

        service.despachar(CLOCK.now());

        RecordatorioEventoDebidoEvent publicado = unicoPublicado();
        assertThat(publicado.lugar()).isEqualTo("Av. Ejercito 123, Arequipa");
        assertThat(publicado.enlace()).isNull();
    }

    // spec 041 FR-031
    @Test
    @DisplayName("un aviso del telefono de siempre no trae nada de mensaje y es un aviso de la app")
    void unAvisoDelTelefonoNoTraeNadaDeMensaje() {
        when(loadRecordatorioPort.vencidosPendientes(any(), anyInt())).thenReturn(List.of(fila(true, null)));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(evento(Formacion.ESCUELA, TipoUbicacion.MEET, "https://meet.google.com/abc")));

        service.despachar(CLOCK.now());

        RecordatorioEventoDebidoEvent publicado = unicoPublicado();
        assertThat(publicado.esAvisoEnApp()).isTrue();
        assertThat(publicado.esAvisoDeMensaje()).isFalse();
    }

    // spec 041 FR-014
    @Test
    @DisplayName("una fila que solo existe para el mensaje se publica como NO aviso del telefono")
    void unaFilaSoloDeMensajeNoEsAvisoDelTelefono() {
        when(loadRecordatorioPort.vencidosPendientes(any(), anyInt())).thenReturn(List.of(fila(false, 60)));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(evento(Formacion.ESCUELA, TipoUbicacion.MEET, "https://meet.google.com/abc")));

        service.despachar(CLOCK.now());

        assertThat(unicoPublicado().esAvisoEnApp()).isFalse();
    }

    // spec 041 FR-031
    @Test
    @DisplayName("una publicacion vieja del outbox (sin los campos nuevos) se lee como un aviso del telefono y no como mensaje")
    void unaPublicacionVieja() {
        var vieja = new RecordatorioEventoDebidoEvent(1L, eventoId.value(), aprendiz, INICIO, "Clase", false, null, "America/Lima", CLOCK.now());

        assertThat(vieja.esAvisoEnApp()).isTrue();
        assertThat(vieja.esAvisoDeMensaje()).isFalse();
        assertThat(vieja.confirmoAsistencia()).isFalse();
    }

    private RecordatorioEvento fila(boolean avisoEnApp, Integer mensajeMinAntes) {
        return RecordatorioEvento.rehydrate(7L, eventoId, INICIO, aprendiz, INICIO.minusSeconds(600), null, null,
                Instant.parse("2026-09-01T00:00:00Z"), avisoEnApp, mensajeMinAntes);
    }

    private Evento evento(Formacion formacion, TipoUbicacion tipo, String valor) {
        return Evento.crear(eventoId, "Clase de manifestación", null, INICIO, 60, LIMA, tipo, valor, TipoAudiencia.TODOS, null, null, null,
                TipoEvento.SESION_ESPECIAL, false, false, false, null, Set.of(), List.of(), aprendiz,
                FixedClock.at(Instant.parse("2026-09-01T00:00:00Z")), formacion);
    }

    private RecordatorioEventoDebidoEvent unicoPublicado() {
        ArgumentCaptor<RecordatorioEventoDebidoEvent> captor = ArgumentCaptor.forClass(RecordatorioEventoDebidoEvent.class);
        verify(events).publishEvent(captor.capture());
        return captor.getValue();
    }
}
