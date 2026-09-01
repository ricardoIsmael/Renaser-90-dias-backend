package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.evento.ActualizarEventoUseCase.ActualizarEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
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
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * CLAUDE.MD §0.3 para TODAS las operaciones de {@link EventoService}, no solo una
 * representativa: la auditoria del modulo encontro que los guards existian pero solo
 * estaban probados en un metodo por servicio. Cada caso de uso se ejercita aca con las
 * dos mitades de la regla — rol sin permiso -> 403 y actor SUSPENDIDO -> 403
 * ({@link NotAuthorizedException}, traducida a 403 por {@code GlobalExceptionHandler}).
 *
 * <p>Vive aparte de {@code EventoServiceTest} (que cubre el comportamiento funcional) para
 * que ninguna de las dos clases pase el techo de 300 lineas de CLAUDE.MD §5.4.8.
 */
@ExtendWith(MockitoExtension.class)
class EventoServiceAutorizacionTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    private static final Instant INICIA_EN = Instant.parse("2026-09-01T19:00:00Z");
    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final UserId ACTOR_ID = UserId.of(UUID.randomUUID());
    private static final UserId OTRO_CREADOR = UserId.of(UUID.randomUUID());
    private static final EventoId EVENTO_ID = EventoId.of(UUID.randomUUID());
    /** Identidad fija: con el id entrando por el puerto IdGenerator, crear() ya no sortea el EventoId. */
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-000000000002");

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
    private IdGenerator idGenerator;

    private EventoService service;

    @BeforeEach
    void setUp() {
        var acceso = new AccesoEventoService(progresoPort, nivelPort, cursoSinAcceso(), (usuario, tipo) -> false);
        service = new EventoService(loadEventoPort, saveEventoPort, loadExcepcionPort, saveExcepcionPort,
                loadConfirmacionPort, saveRecordatorioPort, nivelPort, almacenamientoPort, acceso, CLOCK,
                idGenerator);
        lenient().when(nivelPort.listar()).thenReturn(List.of());
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(loadEventoPort.byId(EVENTO_ID))
                .thenReturn(Optional.of(evento(OTRO_CREADOR, TipoAudiencia.TODOS, Set.of(), TipoEvento.ESPONTANEO)));
    }

    // ─── Operaciones bajo prueba ────────────────────────────────────────────────

    /** Las 6 que pasan por {@code requireRolCreador()} — administrar el calendario. */
    static Stream<Named<Consumer<EventoService>>> operacionesDeAdministracion() {
        return Stream.of(
                operacion("crear", s -> s.crear(comandoCrear())),
                operacion("actualizar", s -> s.actualizar(comandoActualizar())),
                operacion("eliminar", s -> s.eliminar(ACTOR_ID, EVENTO_ID)),
                operacion("cancelarOcurrencia", s -> s.cancelar(ACTOR_ID, EVENTO_ID, INICIA_EN)),
                operacion("solicitarUrlPortada", s -> s.solicitar(ACTOR_ID, EVENTO_ID, "image/png")),
                operacion("confirmarPortada", s -> s.confirmar(ACTOR_ID, EVENTO_ID, "calendar/x/portada-1")));
    }

    /** Las 5 anteriores que ademas cargan el evento, y por eso pasan por {@code requirePropioSiMentor()}. */
    static Stream<Named<Consumer<EventoService>>> operacionesSobreUnEventoExistente() {
        return operacionesDeAdministracion().filter(op -> !"crear".equals(op.getName()));
    }

    /** Las 8 del servicio: las 6 de administracion mas las 2 de lectura. */
    static Stream<Named<Consumer<EventoService>>> todasLasOperaciones() {
        return Stream.concat(
                Stream.of(operacion("listar", s -> s.listar(ACTOR_ID, INICIA_EN, INICIA_EN.plusSeconds(86_400))),
                        operacion("obtener", s -> s.obtener(ACTOR_ID, EVENTO_ID))),
                operacionesDeAdministracion());
    }

    static Stream<Arguments> rolesSinPermisoPorOperacion() {
        return Stream.of(RolUsuario.TRAINEE, RolUsuario.MENTOR_LEAD)
                .flatMap(rol -> operacionesDeAdministracion().map(op -> Arguments.of(rol, op)));
    }

    // ─── §0.3, mitad 1: rol sin permiso -> 403 ──────────────────────────────────

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("rolesSinPermisoPorOperacion")
    @DisplayName("CLAUDE.MD §0.3: un rol sin permiso no administra el calendario -> 403")
    void rolSinPermisoRechazadoEnCadaOperacionDeAdministracion(RolUsuario rol, Consumer<EventoService> operacion) {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(Optional.of(progreso(rol, false, null)));

        assertThatThrownBy(() -> operacion.accept(service)).isInstanceOf(NotAuthorizedException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operacionesSobreUnEventoExistente")
    @DisplayName("CLAUDE.MD §0.3: un MENTOR no toca eventos que no creo -> 403")
    void mentorRechazadoSobreEventoAjenoEnCadaOperacion(Consumer<EventoService> operacion) {
        when(progresoPort.deParticipante(ACTOR_ID))
                .thenReturn(Optional.of(progreso(RolUsuario.MENTOR, false, UUID.randomUUID())));

        assertThatThrownBy(() -> operacion.accept(service)).isInstanceOf(NotAuthorizedException.class);
    }

    // ─── §0.3, mitad 2: actor SUSPENDIDO -> 403 ─────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("todasLasOperaciones")
    @DisplayName("CLAUDE.MD §0.3: un ADMIN SUSPENDIDO recibe 403 aunque su token sea valido")
    void actorSuspendidoRechazadoEnCadaOperacion(Consumer<EventoService> operacion) {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(Optional.of(progreso(RolUsuario.ADMIN, true, null)));

        assertThatThrownBy(() -> operacion.accept(service)).isInstanceOf(NotAuthorizedException.class);
    }

    // ─── Acceso por audiencia (la otra puerta de `obtener`/`listar`) ────────────

    @Test
    @DisplayName("CLAUDE.MD §0.3: obtener un evento cuya audiencia excluye al visor -> 403")
    void obtenerEventoFueraDeLaAudienciaDelVisorEsRechazado() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(Optional.of(progreso(RolUsuario.TRAINEE, false, null)));
        when(loadEventoPort.byId(EVENTO_ID)).thenReturn(Optional.of(
                evento(OTRO_CREADOR, TipoAudiencia.ROLES, Set.of(RolUsuario.ADMIN), TipoEvento.ESPONTANEO)));

        assertThatThrownBy(() -> service.obtener(ACTOR_ID, EVENTO_ID)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: un TRAINEE no elegible no accede a una MENTORIA_ALQUIMISTA -> 403")
    void obtenerEventoQueExigeElegibilidadSinSerElegibleEsRechazado() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(Optional.of(progreso(RolUsuario.TRAINEE, false, null)));
        when(loadEventoPort.byId(EVENTO_ID)).thenReturn(Optional.of(
                evento(OTRO_CREADOR, TipoAudiencia.TODOS, Set.of(), TipoEvento.MENTORIA_ALQUIMISTA)));

        assertThatThrownBy(() -> service.obtener(ACTOR_ID, EVENTO_ID)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("listar no filtra por excepcion sino por omision: el evento ajeno no aparece")
    void listarOmiteLosEventosFueraDeLaAudienciaDelVisor() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(Optional.of(progreso(RolUsuario.TRAINEE, false, null)));
        when(loadEventoPort.candidatosParaVisor(any(), any())).thenReturn(List.of(
                evento(OTRO_CREADOR, TipoAudiencia.ROLES, Set.of(RolUsuario.ADMIN), TipoEvento.ESPONTANEO)));

        assertThat(service.listar(ACTOR_ID, INICIA_EN, INICIA_EN.plusSeconds(86_400))).isEmpty();
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────────

    private static Named<Consumer<EventoService>> operacion(String nombre, Consumer<EventoService> operacion) {
        return Named.of(nombre, operacion);
    }

    private static ProgresoParticipanteCalendar progreso(RolUsuario rol, boolean suspendido, UUID celulaId) {
        return new ProgresoParticipanteCalendar(10, ZONA, rol, suspendido, celulaId);
    }

    private static Evento evento(UserId creador, TipoAudiencia audiencia, Set<RolUsuario> roles, TipoEvento tipo) {
        return Evento.crear(EVENTO_ID, "Sesion", null, INICIA_EN, 60, ZONA, TipoUbicacion.MEET,
                "https://meet.google.com/abc", audiencia, null, null, null, tipo, false, false, false, null, roles,
                List.of(), creador, CLOCK);
    }

    private static CrearEventoCommand comandoCrear() {
        return new CrearEventoCommand(ACTOR_ID, "Sesion", null, INICIA_EN, 60, ZONA, TipoUbicacion.MEET,
                "https://meet.google.com/abc", TipoAudiencia.TODOS, null, null, null, TipoEvento.ESPONTANEO, false,
                false, false, null, Set.of(), List.of());
    }

    private static ActualizarEventoCommand comandoActualizar() {
        return new ActualizarEventoCommand(ACTOR_ID, EVENTO_ID, "Sesion", null, INICIA_EN, 60, ZONA,
                TipoUbicacion.MEET, "https://meet.google.com/abc", TipoAudiencia.TODOS, null, null, null, false,
                false, false, null, Set.of(), List.of());
    }

    private static ResolverAudienciaCursoPort cursoSinAcceso() {
        return new ResolverAudienciaCursoPort() {
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
}
