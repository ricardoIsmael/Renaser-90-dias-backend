package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.api.EventosDelParticipanteFinder;
import com.renaser.os.calendar.api.EventosDelParticipanteFinder.AgendaDeEventos;
import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase.OcurrenciaVista;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort.ProgresoParticipanteCalendar;
import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El rango de {@link EventosDelParticipanteFinder} se arma en DIAS LOCALES de la persona y el
 * filtrado queda en {@link ListarEventosParaVisorUseCase}. Los relojes caen a proposito entre las
 * 00:00 y las 05:00 UTC, que en Lima todavia es el dia anterior (regla 02).
 */
class EventosDelParticipanteServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** 2026-09-24 03:00 UTC = 2026-09-23 22:00 en Lima. */
    private static final FixedClock NOCHE_EN_LIMA = FixedClock.at(Instant.parse("2026-09-24T03:00:00Z"));

    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private final ListarEventosParaVisorUseCase listar = mock(ListarEventosParaVisorUseCase.class);
    private final ConsultarProgresoParticipanteCalendarPort progresoPort =
            mock(ConsultarProgresoParticipanteCalendarPort.class);

    private EventosDelParticipanteService servicio(FixedClock reloj) {
        when(progresoPort.deParticipante(aprendiz)).thenReturn(Optional.of(
                new ProgresoParticipanteCalendar(20, LIMA, RolUsuario.TRAINEE, false, null)));
        return new EventosDelParticipanteService(listar, progresoPort, reloj);
    }

    @Test
    @DisplayName("'hoy' a las 03:00 UTC es el dia anterior en Lima: el rango va de su medianoche a la siguiente")
    void hoyEsElDiaLocal() {
        when(listar.listar(any(), any(), any())).thenReturn(List.of());

        AgendaDeEventos agenda = servicio(NOCHE_EN_LIMA).proximosDias(aprendiz, 1);

        assertThat(agenda.desde()).isEqualTo(LocalDate.of(2026, 9, 23));
        assertThat(agenda.hasta()).isEqualTo(LocalDate.of(2026, 9, 23));
        verify(listar).listar(aprendiz, Instant.parse("2026-09-23T05:00:00Z"), Instant.parse("2026-09-24T05:00:00Z"));
    }

    @Test
    @DisplayName("siete dias cubren de hoy local al sexto dia siguiente, inclusive")
    void sieteDias() {
        when(listar.listar(any(), any(), any())).thenReturn(List.of());

        AgendaDeEventos agenda = servicio(NOCHE_EN_LIMA).proximosDias(aprendiz, 7);

        assertThat(agenda.hasta()).isEqualTo(LocalDate.of(2026, 9, 29));
        verify(listar).listar(aprendiz, Instant.parse("2026-09-23T05:00:00Z"), Instant.parse("2026-09-30T05:00:00Z"));
    }

    @Test
    @DisplayName("cada ocurrencia sale en hora local, con su fin y la confirmacion de la persona")
    void traduceAHoraLocal() {
        Evento evento = evento();
        when(listar.listar(any(), any(), any())).thenReturn(List.of(
                new OcurrenciaVista(evento, null, Instant.parse("2026-09-24T01:00:00Z"),
                        Instant.parse("2026-09-24T01:00:00Z"), 90, "Sesion de celula", EstadoConfirmacion.ASISTE),
                new OcurrenciaVista(evento, null, Instant.parse("2026-09-24T02:00:00Z"),
                        Instant.parse("2026-09-24T02:00:00Z"), null, "Sin duracion", null)));

        AgendaDeEventos agenda = servicio(NOCHE_EN_LIMA).proximosDias(aprendiz, 1);

        var primero = agenda.eventos().get(0);
        assertThat(primero.eventoId()).isEqualTo(evento.id().value());
        assertThat(primero.iniciaLocal()).isEqualTo(LocalDateTime.of(2026, 9, 23, 20, 0));
        assertThat(primero.terminaLocal()).isEqualTo(LocalDateTime.of(2026, 9, 23, 21, 30));
        assertThat(primero.asistencia()).isEqualTo("ASISTE");
        var segundo = agenda.eventos().get(1);
        assertThat(segundo.terminaLocal()).isNull();
        assertThat(segundo.asistencia()).isNull();
    }

    @Test
    @DisplayName("un rango fuera de 1..90 dias se rechaza antes de consultar nada")
    void rangoInvalido() {
        var servicio = new EventosDelParticipanteService(listar, progresoPort, NOCHE_EN_LIMA);

        assertThatThrownBy(() -> servicio.proximosDias(aprendiz, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.proximosDias(aprendiz, EventosDelParticipanteFinder.MAXIMO_DIAS + 1))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(listar, progresoPort);
    }

    @Test
    @DisplayName("el tope es el mismo que el del endpoint")
    void mismoTopeQueElEndpoint() {
        assertThat(EventosDelParticipanteFinder.MAXIMO_DIAS).isEqualTo(ListarEventosParaVisorUseCase.RANGO_MAXIMO_DIAS);
    }

    @Test
    @DisplayName("una persona que no existe no llega al caso de uso")
    void inexistente() {
        when(progresoPort.deParticipante(aprendiz)).thenReturn(Optional.empty());
        var servicio = new EventosDelParticipanteService(listar, progresoPort, NOCHE_EN_LIMA);

        assertThatThrownBy(() -> servicio.proximosDias(aprendiz, 7)).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(listar);
    }

    private Evento evento() {
        return Evento.crear(EventoId.of(UUID.randomUUID()), "Sesion", null, Instant.parse("2026-09-24T01:00:00Z"),
                90, LIMA, TipoUbicacion.MEET, "https://meet.google.com/abc", TipoAudiencia.TODOS, null, null, null,
                TipoEvento.ESPONTANEO, false, false, false, null, Set.of(), List.of(), aprendiz, NOCHE_EN_LIMA);
    }
}
