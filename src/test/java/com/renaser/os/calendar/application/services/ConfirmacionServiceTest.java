package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.out.confirmacion.SaveConfirmacionPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.nivelmembresia.LoadNivelMembresiaPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort.ProgresoParticipanteCalendar;
import com.renaser.os.calendar.application.ports.out.recordatorio.SaveRecordatorioPort;
import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmacionServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-01T00:00:00Z"));
    private static final Instant INICIA_EN = Instant.parse("2026-09-10T19:00:00Z");

    @Mock
    private LoadEventoPort loadEventoPort;
    @Mock
    private SaveConfirmacionPort saveConfirmacionPort;
    @Mock
    private SaveRecordatorioPort saveRecordatorioPort;
    @Mock
    private ConsultarProgresoParticipanteCalendarPort progresoPort;
    @Mock
    private LoadNivelMembresiaPort nivelPort;

    private ConfirmacionService service;
    private final UserId actorId = UserId.of(UUID.randomUUID());
    private final EventoId eventoId = EventoId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        var accesoEventoService = new AccesoEventoService(progresoPort, nivelPort,
                new com.renaser.os.calendar.application.ports.out.curso.ResolverAudienciaCursoPort() {
                    @Override
                    public boolean tieneAcceso(UserId usuarioId, String cursoId) {
                        return false;
                    }

                    @Override
                    public Set<UserId> filtrarConAcceso(String cursoId, Set<UserId> candidatos) {
                        return Set.of();
                    }
                }, (u, t) -> false);
        service = new ConfirmacionService(loadEventoPort, saveConfirmacionPort, saveRecordatorioPort,
                accesoEventoService, CLOCK);
    }

    private Evento eventoTodos(UserId creador) {
        return Evento.crear(eventoId, "Sesion", null, INICIA_EN, 60, ZoneId.of("America/Lima"),
                TipoUbicacion.MEET, "https://meet.google.com/abc", TipoAudiencia.TODOS, null, null, null,
                TipoEvento.ESPONTANEO, false, false, false, null, Set.of(), List.of(), creador, CLOCK);
    }

    @Test
    void confirmarAsisteCancelaLosRecordatoriosPendientes() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(
                new ProgresoParticipanteCalendar(10, ZoneId.of("America/Lima"), RolUsuario.TRAINEE, false, null)));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(eventoTodos(actorId)));

        service.confirmar(actorId, eventoId, INICIA_EN, EstadoConfirmacion.ASISTE);

        verify(saveConfirmacionPort).upsert(any());
        verify(saveRecordatorioPort).cancelarPorAsistencia(actorId, eventoId, INICIA_EN, RecordatorioEvento.MOTIVO_ASISTIRA);
    }

    @Test
    void confirmarNoAsisteNoCancelaRecordatorios() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(
                new ProgresoParticipanteCalendar(10, ZoneId.of("America/Lima"), RolUsuario.TRAINEE, false, null)));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(eventoTodos(actorId)));

        service.confirmar(actorId, eventoId, INICIA_EN, EstadoConfirmacion.NO_ASISTE);

        verify(saveConfirmacionPort).upsert(any());
        verify(saveRecordatorioPort, never()).cancelarPorAsistencia(any(), any(), any(), any());
    }

    @Test
    void ocurrenciaQueNoPerteneceAlEventoEsRechazada() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(
                new ProgresoParticipanteCalendar(10, ZoneId.of("America/Lima"), RolUsuario.TRAINEE, false, null)));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(eventoTodos(actorId)));

        Instant ocurrenciaInexistente = INICIA_EN.plusSeconds(3600 * 24);
        assertThatThrownBy(() -> service.confirmar(actorId, eventoId, ocurrenciaInexistente, EstadoConfirmacion.ASISTE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cuentaSuspendidaNoPuedeConfirmar() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(
                new ProgresoParticipanteCalendar(10, ZoneId.of("America/Lima"), RolUsuario.TRAINEE, true, null)));

        assertThatThrownBy(() -> service.confirmar(actorId, eventoId, INICIA_EN, EstadoConfirmacion.ASISTE))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: confirmar asistencia a un evento fuera de la audiencia del visor -> 403")
    void eventoFueraDeLaAudienciaDelVisorNoSePuedeConfirmar() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(
                new ProgresoParticipanteCalendar(10, ZoneId.of("America/Lima"), RolUsuario.TRAINEE, false, null)));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(eventoSoloParaAdmins()));

        assertThatThrownBy(() -> service.confirmar(actorId, eventoId, INICIA_EN, EstadoConfirmacion.ASISTE))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: un TRAINEE no elegible no confirma una MENTORIA_ALQUIMISTA -> 403")
    void eventoQueExigeElegibilidadNoSePuedeConfirmarSinSerElegible() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(
                new ProgresoParticipanteCalendar(10, ZoneId.of("America/Lima"), RolUsuario.TRAINEE, false, null)));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(eventoMentoriaAlquimista()));

        assertThatThrownBy(() -> service.confirmar(actorId, eventoId, INICIA_EN, EstadoConfirmacion.ASISTE))
                .isInstanceOf(NotAuthorizedException.class);
    }

    private Evento eventoSoloParaAdmins() {
        return Evento.crear(eventoId, "Sesion", null, INICIA_EN, 60, ZoneId.of("America/Lima"),
                TipoUbicacion.MEET, "https://meet.google.com/abc", TipoAudiencia.ROLES, null, null, null,
                TipoEvento.ESPONTANEO, false, false, false, null, Set.of(RolUsuario.ADMIN), List.of(), actorId,
                CLOCK);
    }

    private Evento eventoMentoriaAlquimista() {
        return Evento.crear(eventoId, "Mentoria", null, INICIA_EN, 60, ZoneId.of("America/Lima"),
                TipoUbicacion.MEET, "https://meet.google.com/abc", TipoAudiencia.TODOS, null, null, null,
                TipoEvento.MENTORIA_ALQUIMISTA, false, false, false, null, Set.of(), List.of(), actorId, CLOCK);
    }
}
