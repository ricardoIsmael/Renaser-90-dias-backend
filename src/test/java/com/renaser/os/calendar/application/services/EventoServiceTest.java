package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.EventoVista;
import com.renaser.os.calendar.application.ports.out.confirmacion.LoadConfirmacionPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadExcepcionPort;
import com.renaser.os.calendar.application.ports.out.evento.SaveEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.SaveExcepcionPort;
import com.renaser.os.calendar.application.ports.out.nivelmembresia.LoadNivelMembresiaPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort.ProgresoParticipanteCalendar;
import com.renaser.os.calendar.application.ports.out.recordatorio.SaveRecordatorioPort;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private LoadEventoPort loadEventoPort;
    @Mock
    private SaveEventoPort saveEventoPort;
    @Mock
    private LoadExcepcionPort loadExcepcionPort;
    @Mock
    private SaveExcepcionPort saveExcepcionPort;
    @Mock
    private LoadConfirmacionPort loadConfirmacionPort;
    @Mock
    private SaveRecordatorioPort saveRecordatorioPort;
    @Mock
    private LoadNivelMembresiaPort nivelPort;
    @Mock
    private AlmacenamientoPort almacenamientoPort;
    @Mock
    private ConsultarProgresoParticipanteCalendarPort progresoPort;

    private AccesoEventoService accesoEventoService;
    private EventoService service;

    private final UserId actorId = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        accesoEventoService = new AccesoEventoService(progresoPort, nivelPort, cursoIdNoOp(), (u, t) -> false);
        service = new EventoService(loadEventoPort, saveEventoPort, loadExcepcionPort, saveExcepcionPort,
                loadConfirmacionPort, saveRecordatorioPort, nivelPort, almacenamientoPort, accesoEventoService, CLOCK);
        lenient().when(nivelPort.listar()).thenReturn(List.of());
    }

    private static com.renaser.os.calendar.application.ports.out.curso.ResolverAudienciaCursoPort cursoIdNoOp() {
        return new com.renaser.os.calendar.application.ports.out.curso.ResolverAudienciaCursoPort() {
            @Override
            public boolean tieneAcceso(UserId usuarioId, String cursoId) {
                return false;
            }

            @Override
            public Set<UserId> filtrarConAcceso(String cursoId, Set<UserId> candidatos) {
                return Set.of();
            }
        };
    }

    private ProgresoParticipanteCalendar progreso(RolUsuario rol) {
        return new ProgresoParticipanteCalendar(10, ZoneId.of("America/Lima"), rol, false, null);
    }

    private CrearEventoCommand comandoCrear(UUID celulaDestinoId) {
        return new CrearEventoCommand(actorId, "Sesion", null, Instant.parse("2026-09-01T19:00:00Z"), 60,
                ZoneId.of("America/Lima"), TipoUbicacion.MEET, "https://meet.google.com/abc", TipoAudiencia.TODOS,
                null, null, celulaDestinoId, TipoEvento.ESPONTANEO, false, false, false, null, Set.of(), List.of());
    }

    @Test
    void adminPuedeCrearEventoConAudienciaTodos() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolUsuario.ADMIN)));
        when(saveEventoPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        EventoVista vista = service.crear(comandoCrear(null));

        assertThat(vista.evento().tipoAudiencia()).isEqualTo(TipoAudiencia.TODOS);
        assertThat(vista.coverUrl()).isNull();
        verify(saveEventoPort).guardar(any());
    }

    @Test
    void traineeNoPuedeCrearEventos() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolUsuario.TRAINEE)));

        assertThatThrownBy(() -> service.crear(comandoCrear(null))).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void mentorFuerzaAudienciaCelulaConLaSuyaPropia() {
        UUID celulaLiderada = UUID.randomUUID();
        ProgresoParticipanteCalendar progresoMentor = new ProgresoParticipanteCalendar(0, ZoneId.of("America/Lima"),
                RolUsuario.MENTOR, false, celulaLiderada);
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progresoMentor));
        when(saveEventoPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        // El comando pide TODOS y una celula distinta — el service debe ignorarlo y forzar la propia.
        EventoVista vista = service.crear(comandoCrear(UUID.randomUUID()));

        assertThat(vista.evento().tipoAudiencia()).isEqualTo(TipoAudiencia.CELULA);
        assertThat(vista.evento().celulaDestinoId()).isEqualTo(celulaLiderada);
    }

    @Test
    void mentorSinCelulaLideradaNoPuedeCrear() {
        ProgresoParticipanteCalendar progresoMentor = new ProgresoParticipanteCalendar(0, ZoneId.of("America/Lima"),
                RolUsuario.MENTOR, false, null);
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progresoMentor));

        assertThatThrownBy(() -> service.crear(comandoCrear(null))).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void mentorNoPuedeEditarEventoQueNoCreo() {
        EventoId eventoId = EventoId.newId();
        UserId otroCreador = UserId.of(UUID.randomUUID());
        Evento eventoAjeno = Evento.crear("Sesion", null, Instant.parse("2026-09-01T19:00:00Z"), 60,
                ZoneId.of("America/Lima"), TipoUbicacion.MEET, "https://meet.google.com/abc", TipoAudiencia.TODOS,
                null, null, null, TipoEvento.ESPONTANEO, false, false, false, null, Set.of(), List.of(), otroCreador,
                CLOCK);

        ProgresoParticipanteCalendar progresoMentor = new ProgresoParticipanteCalendar(0, ZoneId.of("America/Lima"),
                RolUsuario.MENTOR, false, UUID.randomUUID());
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progresoMentor));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(eventoAjeno));

        var command = new com.renaser.os.calendar.application.ports.in.evento.ActualizarEventoUseCase.ActualizarEventoCommand(
                actorId, eventoId, "Otro titulo", null, Instant.parse("2026-09-01T19:00:00Z"), 60,
                ZoneId.of("America/Lima"), TipoUbicacion.MEET, "https://meet.google.com/abc", TipoAudiencia.TODOS,
                null, null, null, false, false, false, null, Set.of(), List.of());

        assertThatThrownBy(() -> service.actualizar(command)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void eliminarBorraLaPortadaSiExiste() {
        EventoId eventoId = EventoId.newId();
        Evento evento = Evento.crear("Sesion", null, Instant.parse("2026-09-01T19:00:00Z"), 60,
                ZoneId.of("America/Lima"), TipoUbicacion.MEET, "https://meet.google.com/abc", TipoAudiencia.TODOS,
                null, null, null, TipoEvento.ESPONTANEO, false, false, false, null, Set.of(), List.of(), actorId,
                CLOCK);
        evento.fijarPortada("calendar/x/portada-1");

        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolUsuario.ADMIN)));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(evento));

        service.eliminar(actorId, eventoId);

        verify(almacenamientoPort).borrar("calendar/x/portada-1");
        verify(saveEventoPort).eliminar(eventoId);
    }

    @Test
    void cancelarOcurrenciaDeEventoNoRecurrenteFalla() {
        EventoId eventoId = EventoId.newId();
        Evento evento = Evento.crear("Sesion", null, Instant.parse("2026-09-01T19:00:00Z"), 60,
                ZoneId.of("America/Lima"), TipoUbicacion.MEET, "https://meet.google.com/abc", TipoAudiencia.TODOS,
                null, null, null, TipoEvento.ESPONTANEO, false, false, false, null, Set.of(), List.of(), actorId,
                CLOCK);

        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolUsuario.ADMIN)));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> service.cancelar(actorId, eventoId, Instant.parse("2026-09-01T19:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
