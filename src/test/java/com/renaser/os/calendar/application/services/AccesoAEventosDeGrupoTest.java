package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.out.celula.ConsultarPertenenciaAGrupoPort;
import com.renaser.os.calendar.application.ports.out.confirmacion.LoadConfirmacionPort;
import com.renaser.os.calendar.application.ports.out.curso.ResolverAudienciaCursoPort;
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
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E-363: quien puede abrir y ver en su lista un evento de grupo. La pertenencia al grupo la da un puerto
 * (asignaciones vigentes de {@code community}); aca se prueba que el servicio la use, y cuando no hace falta.
 *
 * <p>Contra el codigo anterior fallan las tres primeras: el acceso comparaba el grupo del evento solo con
 * {@code participantes_programa.celula_id}, que un mentor no tiene y que nombra un solo grupo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccesoAEventosDeGrupoTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-27T03:00:00Z"));
    private static final Instant INICIA_EN = Instant.parse("2026-09-28T00:00:00Z");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final UserId ACTOR = UserId.of(UUID.randomUUID());
    private static final UserId ADMIN = UserId.of(UUID.randomUUID());
    private static final EventoId EVENTO = EventoId.of(UUID.randomUUID());
    private static final UUID GRUPO_DEL_EVENTO = UUID.randomUUID();
    private static final UUID OTRO_GRUPO = UUID.randomUUID();

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
    @Mock
    private ResolverAudienciaCursoPort cursoPort;
    @Mock
    private ConsultarPertenenciaAGrupoPort pertenencia;
    @Mock
    private IdGenerator idGenerator;

    private EventoService service;

    @BeforeEach
    void setUp() {
        var acceso = new AccesoEventoService(progresoPort, nivelPort, cursoPort, (usuario, tipo) -> true, pertenencia);
        service = new EventoService(loadEventoPort, saveEventoPort, loadExcepcionPort, saveExcepcionPort,
                loadConfirmacionPort, saveRecordatorioPort, nivelPort, almacenamientoPort, acceso, CLOCK,
                idGenerator);
        when(nivelPort.listar()).thenReturn(List.of());
        when(loadEventoPort.byId(EVENTO)).thenReturn(Optional.of(eventoDelGrupo()));
        when(loadEventoPort.candidatosParaVisor(any(), any())).thenReturn(List.of(eventoDelGrupo()));
    }

    @Test
    @DisplayName("E-363: el mentor del grupo, que no tiene puntero de grupo, abre el evento y lo ve en su lista")
    void elMentorDelGrupoAbreElEvento() {
        visor(RolUsuario.MENTOR, null);
        when(pertenencia.perteneceHoy(GRUPO_DEL_EVENTO, ACTOR)).thenReturn(true);

        assertThat(service.obtener(ACTOR, EVENTO).evento().id()).isEqualTo(EVENTO);
        assertThat(service.listar(ACTOR, INICIA_EN.minusSeconds(3600), INICIA_EN.plusSeconds(3600))).hasSize(1);
    }

    @Test
    @DisplayName("E-363: un mentor que lidera varios grupos (D-141) abre el evento de cualquiera de ellos")
    void elMentorDeVariosGruposAbreElDeCadaUno() {
        visor(RolUsuario.MENTOR, OTRO_GRUPO);
        when(pertenencia.perteneceHoy(GRUPO_DEL_EVENTO, ACTOR)).thenReturn(true);

        assertThat(service.obtener(ACTOR, EVENTO).evento().id()).isEqualTo(EVENTO);
    }

    @Test
    @DisplayName("E-363: un aprendiz sumado al grupo (D-139), con su puntero en el principal, abre el evento")
    void elAprendizAdicionalAbreElEvento() {
        visor(RolUsuario.TRAINEE, OTRO_GRUPO);
        when(pertenencia.perteneceHoy(GRUPO_DEL_EVENTO, ACTOR)).thenReturn(true);

        assertThat(service.obtener(ACTOR, EVENTO).evento().id()).isEqualTo(EVENTO);
    }

    @Test
    @DisplayName("E-363: quien no pertenece al grupo sigue recibiendo 403, y no lo ve en su lista")
    void quienNoPerteneceSigueAfuera() {
        visor(RolUsuario.TRAINEE, OTRO_GRUPO);
        when(pertenencia.perteneceHoy(GRUPO_DEL_EVENTO, ACTOR)).thenReturn(false);

        assertThatThrownBy(() -> service.obtener(ACTOR, EVENTO))
                .isInstanceOf(NotAuthorizedException.class)
                .hasMessage("No tienes acceso a este evento");
        assertThat(service.listar(ACTOR, INICIA_EN.minusSeconds(3600), INICIA_EN.plusSeconds(3600))).isEmpty();
    }

    @Test
    @DisplayName("E-363: con el puntero en el grupo del evento alcanza, sin consultar las asignaciones")
    void elPunteroAlcanzaSinConsultar() {
        visor(RolUsuario.TRAINEE, GRUPO_DEL_EVENTO);

        assertThat(service.obtener(ACTOR, EVENTO).evento().id()).isEqualTo(EVENTO);
        verify(pertenencia, never()).perteneceHoy(any(), any());
    }

    @Test
    @DisplayName("E-363: ADMIN ve todo sin consultar las asignaciones")
    void elAdminNoConsulta() {
        when(progresoPort.deParticipante(ADMIN)).thenReturn(Optional.of(
                new ProgresoParticipanteCalendar(0, LIMA, RolUsuario.ADMIN, false, null)));

        assertThat(service.obtener(ADMIN, EVENTO).evento().id()).isEqualTo(EVENTO);
        verify(pertenencia, never()).perteneceHoy(any(), any());
    }

    @Test
    @DisplayName("E-363: una cuenta suspendida sigue recibiendo 403 aunque pertenezca al grupo")
    void elSuspendidoSigueAfuera() {
        when(progresoPort.deParticipante(ACTOR)).thenReturn(Optional.of(
                new ProgresoParticipanteCalendar(10, LIMA, RolUsuario.MENTOR, true, null)));
        when(pertenencia.perteneceHoy(GRUPO_DEL_EVENTO, ACTOR)).thenReturn(true);

        assertThatThrownBy(() -> service.obtener(ACTOR, EVENTO)).isInstanceOf(NotAuthorizedException.class);
    }

    private void visor(RolUsuario rol, UUID punteroDeGrupo) {
        when(progresoPort.deParticipante(ACTOR)).thenReturn(Optional.of(
                new ProgresoParticipanteCalendar(10, LIMA, rol, false, punteroDeGrupo)));
    }

    private static Evento eventoDelGrupo() {
        return Evento.crear(EVENTO, "Sesion del grupo", null, INICIA_EN, 60, LIMA, TipoUbicacion.MEET,
                "https://meet.google.com/abc", TipoAudiencia.CELULA, null, null, GRUPO_DEL_EVENTO,
                TipoEvento.ESPONTANEO, false, false, false, null, Set.of(), List.of(), ADMIN, CLOCK);
    }
}
