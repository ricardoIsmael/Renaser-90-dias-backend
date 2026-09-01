package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.in.celula.ActualizarCelulaUseCase.ActualizarCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.AsignarAprendizCelulaUseCase.AsignarAprendizCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.AsignarMentorCelulaUseCase.AsignarMentorCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.EliminarCelulaUseCase.EliminarCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.ProgramarSesionCelulaUseCase.ProgramarSesionCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.QuitarAprendizCelulaUseCase.QuitarAprendizCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.QuitarMentorCelulaUseCase.QuitarMentorCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.CrearCelulaUseCase.CrearCelulaCommand;
import com.renaser.os.community.application.ports.out.celula.EliminarCelulaPort;
import com.renaser.os.community.application.ports.out.celula.ExistePerfilMentorPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.application.ports.out.celula.SaveCelulaPort;
import com.renaser.os.community.application.ports.out.cohorte.LoadCohortePort;
import com.renaser.os.community.application.ports.out.participante.ConsultarCelulaDeParticipantePort;
import com.renaser.os.community.application.ports.out.participante.ConsultarMiembrosCelulaPort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AsignacionCelulaPort;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CelulaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    /** Identidad fija: con el id entrando por el puerto IdGenerator, la factoria del agregado ya no lo sortea. */
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Mock
    private LoadCelulaPort loadCelulaPort;
    @Mock
    private SaveCelulaPort saveCelulaPort;
    @Mock
    private EliminarCelulaPort eliminarCelulaPort;
    @Mock
    private LoadCohortePort loadCohortePort;
    @Mock
    private ExistePerfilMentorPort existePerfilMentorPort;
    @Mock
    private ConsultarMiembrosCelulaPort consultarMiembrosCelulaPort;
    @Mock
    private ConsultarCelulaDeParticipantePort consultarCelulaDeParticipantePort;
    @Mock
    private ConsultarPerfilUsuarioPort consultarPerfilUsuarioPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private ParticipacionProgramaFinder participacionProgramaFinder;
    @Mock
    private AsignacionCelulaPort asignacionCelulaPort;
    @Mock
    private org.springframework.context.ApplicationEventPublisher events;
    @Mock
    private IdGenerator idGenerator;

    private CelulaService service;

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId mentor = UserId.of(UUID.randomUUID());
    private final UserId trainee = UserId.of(UUID.randomUUID());
    private final UserId adminSuspendido = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new CelulaService(loadCelulaPort, saveCelulaPort, eliminarCelulaPort, loadCohortePort,
                existePerfilMentorPort, consultarMiembrosCelulaPort, consultarCelulaDeParticipantePort,
                consultarPerfilUsuarioPort, userSummaryFinder, participacionProgramaFinder, asignacionCelulaPort,
                events, CLOCK, idGenerator);
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(userSummaryFinder.findById(admin))
                .thenReturn(Optional.of(new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(mentor))
                .thenReturn(Optional.of(new UserSummary(mentor, "Mentor", null, UserRole.MENTOR, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(trainee))
                .thenReturn(Optional.of(new UserSummary(trainee, "Aprendiz", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(adminSuspendido)).thenReturn(Optional.of(new UserSummary(
                adminSuspendido, "Admin suspendido", null, UserRole.ADMIN, UserStatus.SUSPENDED)));
    }

    private Celula celulaExistente() {
        return Celula.rehydrate(CelulaId.of(UUID.randomUUID()), "Celula 1", null,
                CohorteId.of(UUID.randomUUID()), null, null, CLOCK.now(), CLOCK.now());
    }

    @Test
    void crearComoMentorEsRechazado() {
        var command = new CrearCelulaCommand(mentor, "Celula 1", CohorteId.of(UUID.randomUUID()), null);
        assertThatThrownBy(() -> service.crear(command)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void asignarMentorSinPerfilPropioFalla() {
        Celula celula = celulaExistente();
        when(loadCelulaPort.porId(celula.id())).thenReturn(Optional.of(celula));
        when(existePerfilMentorPort.existe(mentor)).thenReturn(false);
        var command = new AsignarMentorCelulaCommand(admin, celula.id(), mentor);
        assertThatThrownBy(() -> service.asignar(command)).isInstanceOf(IllegalStateException.class);
        verify(saveCelulaPort, never()).save(any());
    }

    @Test
    void asignarUnTraineeComoLiderFalla() {
        Celula celula = celulaExistente();
        when(loadCelulaPort.porId(celula.id())).thenReturn(Optional.of(celula));
        var command = new AsignarMentorCelulaCommand(admin, celula.id(), trainee);
        assertThatThrownBy(() -> service.asignar(command)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void asignarMentorYaLiderDeOtraCelulaFalla() {
        Celula celula = celulaExistente();
        Celula otraCelula = celulaExistente();
        when(loadCelulaPort.porId(celula.id())).thenReturn(Optional.of(celula));
        when(existePerfilMentorPort.existe(mentor)).thenReturn(true);
        when(loadCelulaPort.porMentor(mentor)).thenReturn(Optional.of(otraCelula));
        var command = new AsignarMentorCelulaCommand(admin, celula.id(), mentor);
        assertThatThrownBy(() -> service.asignar(command)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void asignarMentorElegibleFunciona() {
        Celula celula = celulaExistente();
        when(loadCelulaPort.porId(celula.id())).thenReturn(Optional.of(celula));
        when(existePerfilMentorPort.existe(mentor)).thenReturn(true);
        when(loadCelulaPort.porMentor(mentor)).thenReturn(Optional.empty());
        when(saveCelulaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var command = new AsignarMentorCelulaCommand(admin, celula.id(), mentor);
        var detalle = service.asignar(command);
        assertThat(detalle.celula().mentorId()).isEqualTo(mentor);
    }

    @Test
    void asignarAprendizComoMentorEsRechazado() {
        Celula celula = celulaExistente();
        var command = new AsignarAprendizCelulaCommand(mentor, celula.id(), trainee);
        assertThatThrownBy(() -> service.asignar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(asignacionCelulaPort, never()).asignarCelula(any(), any(), any());
    }

    @Test
    void asignarUnMentorComoAprendizFalla() {
        Celula celula = celulaExistente();
        when(loadCelulaPort.porId(celula.id())).thenReturn(Optional.of(celula));
        var command = new AsignarAprendizCelulaCommand(admin, celula.id(), mentor);
        assertThatThrownBy(() -> service.asignar(command)).isInstanceOf(IllegalArgumentException.class);
        verify(asignacionCelulaPort, never()).asignarCelula(any(), any(), any());
    }

    @Test
    void asignarAprendizElegibleDelegaEnUsers() {
        Celula celula = celulaExistente();
        when(loadCelulaPort.porId(celula.id())).thenReturn(Optional.of(celula));
        var command = new AsignarAprendizCelulaCommand(admin, celula.id(), trainee);

        service.asignar(command);

        verify(asignacionCelulaPort).asignarCelula(admin, trainee, celula.id().value());
    }

    @Test
    void quitarAprendizComoMentorEsRechazado() {
        var command = new QuitarAprendizCelulaCommand(mentor, trainee);
        assertThatThrownBy(() -> service.quitar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(asignacionCelulaPort, never()).quitarCelula(any(), any());
    }

    @Test
    void quitarAprendizDelegaEnUsers() {
        var command = new QuitarAprendizCelulaCommand(admin, trainee);

        service.quitar(command);

        verify(asignacionCelulaPort).quitarCelula(admin, trainee);
    }

    @Test
    void miCelulaSinCelulaAsignadaEsVacio() {
        when(consultarCelulaDeParticipantePort.celulaDeUsuario(trainee)).thenReturn(Optional.empty());
        assertThat(service.miCelula(trainee)).isEmpty();
    }

    @Test
    void celulaDeParticipanteSinCelulaAsignadaEsVacio() {
        when(consultarCelulaDeParticipantePort.celulaDeUsuario(trainee)).thenReturn(Optional.empty());
        assertThat(service.celulaDeParticipante(trainee)).isEmpty();
    }

    @Test
    void celulaDeParticipanteProyectaNombreDeCohorteYMentor() {
        Celula celula = celulaExistente();
        Celula otraCelulaMismoCohorte = Celula.rehydrate(CelulaId.of(UUID.randomUUID()), "Celula 2", null,
                celula.cohorteId(), null, null, CLOCK.now(), CLOCK.now());
        celula.asignarMentor(mentor, CLOCK.now());
        var cohorte = com.renaser.os.community.domain.model.cohorte.Cohorte.rehydrate(celula.cohorteId(),
                "Cohorte Agosto", java.time.LocalDate.now(), null,
                com.renaser.os.community.domain.model.cohorte.EstadoCohorte.ACTIVA, CLOCK.now(), CLOCK.now());

        when(consultarCelulaDeParticipantePort.celulaDeUsuario(trainee)).thenReturn(Optional.of(celula.id()));
        when(loadCelulaPort.porId(celula.id())).thenReturn(Optional.of(celula));
        when(loadCohortePort.porId(celula.cohorteId())).thenReturn(Optional.of(cohorte));
        when(consultarPerfilUsuarioPort.porId(mentor))
                .thenReturn(Optional.of(new com.renaser.os.community.application.ports.out.usuario
                        .ConsultarPerfilUsuarioPort.PerfilUsuario(mentor, "Mentor Uno", null)));
        when(consultarMiembrosCelulaPort.contarMiembros(celula.id())).thenReturn(7);
        when(loadCelulaPort.porCohorte(celula.cohorteId())).thenReturn(java.util.List.of(celula, otraCelulaMismoCohorte));

        var resumen = service.celulaDeParticipante(trainee).orElseThrow();

        assertThat(resumen.celulaId()).isEqualTo(celula.id().value());
        assertThat(resumen.cellName()).isEqualTo("Celula 1");
        assertThat(resumen.cohortName()).isEqualTo("Cohorte Agosto");
        assertThat(resumen.mentorName()).isEqualTo("Mentor Uno");
        assertThat(resumen.memberCount()).isEqualTo(7);
        assertThat(resumen.totalCellsInCohort()).isEqualTo(2);
    }

    // ─── #25: panel admin de celulas (dashboard cross-cohorte + pickers) ───────────────

    @Test
    @DisplayName("CLAUDE.MD sec. 0.3: un MENTOR no puede ver el dashboard cross-cohorte")
    void dashboardComoMentorEsRechazado() {
        assertThatThrownBy(() -> service.dashboard(mentor)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void dashboardListaCelulasDeTodasLasCohortesConSuCohorte() {
        Celula celula = celulaExistente();
        var cohorte = com.renaser.os.community.domain.model.cohorte.Cohorte.rehydrate(celula.cohorteId(), "Cohorte 1",
                java.time.LocalDate.now(), null, com.renaser.os.community.domain.model.cohorte.EstadoCohorte.ACTIVA,
                CLOCK.now(), CLOCK.now());
        when(loadCelulaPort.todas()).thenReturn(java.util.List.of(celula));
        when(loadCohortePort.porId(celula.cohorteId())).thenReturn(Optional.of(cohorte));
        when(consultarMiembrosCelulaPort.contarMiembros(celula.id())).thenReturn(3);

        var dashboard = service.dashboard(admin);

        assertThat(dashboard).hasSize(1);
        assertThat(dashboard.get(0).cohorte().nombre()).isEqualTo("Cohorte 1");
        assertThat(dashboard.get(0).cantidadMiembros()).isEqualTo(3);
    }

    @Test
    @DisplayName("CLAUDE.MD sec. 0.3: un MENTOR no puede listar mentores-disponibles")
    void mentoresDisponiblesComoMentorEsRechazado() {
        assertThatThrownBy(() -> service.mentoresDisponibles(mentor)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void mentoresDisponiblesExcluyeAQuienesYaLideranUnaCelula() {
        UserId mentorLibre = UserId.of(UUID.randomUUID());
        Celula celulaConMentor = celulaExistente();
        celulaConMentor.asignarMentor(mentor, CLOCK.now());
        when(loadCelulaPort.todas()).thenReturn(java.util.List.of(celulaConMentor));
        when(participacionProgramaFinder.usuariosActivosConRol(java.util.Set.of(UserRole.MENTOR)))
                .thenReturn(java.util.List.of(mentor, mentorLibre));
        when(userSummaryFinder.findById(mentorLibre)).thenReturn(
                Optional.of(new UserSummary(mentorLibre, "Mentor Libre", null, UserRole.MENTOR, UserStatus.ACTIVE)));

        var disponibles = service.mentoresDisponibles(admin);

        assertThat(disponibles).hasSize(1);
        assertThat(disponibles.get(0).userId()).isEqualTo(mentorLibre);
        assertThat(disponibles.get(0).celulaActual()).isNull();
    }

    @Test
    void mentoresMarcaLaCelulaActualDeQuienYaLidera() {
        Celula celulaConMentor = celulaExistente();
        celulaConMentor.asignarMentor(mentor, CLOCK.now());
        when(loadCelulaPort.todas()).thenReturn(java.util.List.of(celulaConMentor));
        when(participacionProgramaFinder.usuariosActivosConRol(java.util.Set.of(UserRole.MENTOR)))
                .thenReturn(java.util.List.of(mentor));

        var todos = service.mentores(admin);

        assertThat(todos).hasSize(1);
        assertThat(todos.get(0).celulaActual()).isEqualTo(celulaConMentor.id());
    }

    @Test
    @DisplayName("CLAUDE.MD sec. 0.3: un MENTOR no puede listar aprendices-disponibles")
    void aprendicesDisponiblesComoMentorEsRechazado() {
        assertThatThrownBy(() -> service.aprendicesDisponibles(mentor)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void aprendicesDisponiblesExcluyeAQuienesYaTienenCelula() {
        UserId aprendizAsignado = UserId.of(UUID.randomUUID());
        UserId aprendizLibre = UserId.of(UUID.randomUUID());
        Celula celula = celulaExistente();
        when(loadCelulaPort.todas()).thenReturn(java.util.List.of(celula));
        when(participacionProgramaFinder.miembrosDeCelula(celula.id().value()))
                .thenReturn(java.util.List.of(aprendizAsignado));
        when(participacionProgramaFinder.usuariosActivosConRol(java.util.Set.of(UserRole.TRAINEE)))
                .thenReturn(java.util.List.of(aprendizAsignado, aprendizLibre));
        when(userSummaryFinder.findByIds(java.util.List.of(aprendizLibre))).thenReturn(java.util.Map.of(aprendizLibre,
                new UserSummary(aprendizLibre, "Aprendiz Libre", null, UserRole.TRAINEE, UserStatus.ACTIVE)));

        var disponibles = service.aprendicesDisponibles(admin);

        assertThat(disponibles).hasSize(1);
        assertThat(disponibles.get(0).userId()).isEqualTo(aprendizLibre);
        assertThat(disponibles.get(0).nombreCompleto()).isEqualTo("Aprendiz Libre");
    }

    // ─── CLAUDE.MD sec. 0.3: 403 por rol y 403 por cuenta SUSPENDIDA, metodo por metodo ──
    // `requireAdmin`/`requireActorActivo` ya estaban en los 16 metodos; faltaba probarlos
    // en 25 de las 30 combinaciones (solo estaban cubiertos crear/asignar-aprendiz/
    // quitar-aprendiz/dashboard/mentores-disponibles/aprendices-disponibles, y solo por rol).

    @Test
    @DisplayName("actualizar(): rol sin permiso (MENTOR) -> 403, nunca guarda")
    void actualizarComoMentorEsRechazado() {
        var command = new ActualizarCelulaCommand(mentor, CelulaId.of(UUID.randomUUID()), "Otro", null, true);
        assertThatThrownBy(() -> service.actualizar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveCelulaPort, never()).save(any());
    }

    @Test
    @DisplayName("asignar(mentor): rol sin permiso (MENTOR) -> 403, nunca guarda")
    void asignarMentorComoMentorEsRechazado() {
        var command = new AsignarMentorCelulaCommand(mentor, CelulaId.of(UUID.randomUUID()), mentor);
        assertThatThrownBy(() -> service.asignar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveCelulaPort, never()).save(any());
    }

    @Test
    @DisplayName("quitar(mentor): rol sin permiso (MENTOR) -> 403, nunca guarda")
    void quitarMentorComoMentorEsRechazado() {
        var command = new QuitarMentorCelulaCommand(mentor, CelulaId.of(UUID.randomUUID()));
        assertThatThrownBy(() -> service.quitar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveCelulaPort, never()).save(any());
    }

    @Test
    @DisplayName("programar(): rol sin permiso (MENTOR) -> 403, nunca guarda")
    void programarSesionComoMentorEsRechazado() {
        var command = new ProgramarSesionCelulaCommand(mentor, CelulaId.of(UUID.randomUUID()), CLOCK.now());
        assertThatThrownBy(() -> service.programar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveCelulaPort, never()).save(any());
    }

    @Test
    @DisplayName("eliminar(): rol sin permiso (MENTOR) -> 403, nunca borra")
    void eliminarComoMentorEsRechazado() {
        var command = new EliminarCelulaCommand(mentor, CelulaId.of(UUID.randomUUID()));
        assertThatThrownBy(() -> service.eliminar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(eliminarCelulaPort, never()).eliminar(any());
    }

    @Test
    @DisplayName("mentores(): rol sin permiso (MENTOR) -> 403")
    void mentoresComoMentorEsRechazado() {
        assertThatThrownBy(() -> service.mentores(mentor)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("listarPorCohorte(): rol sin permiso (TRAINEE) -> 403")
    void listarPorCohorteComoTraineeEsRechazado() {
        CohorteId cohorteId = CohorteId.of(UUID.randomUUID());
        assertThatThrownBy(() -> service.listarPorCohorte(trainee, cohorteId))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("obtener(): rol sin permiso (TRAINEE) -> 403")
    void obtenerComoTraineeEsRechazado() {
        Celula celula = celulaExistente();
        when(loadCelulaPort.porId(celula.id())).thenReturn(Optional.of(celula));
        assertThatThrownBy(() -> service.obtener(trainee, celula.id())).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("obtener(): un MENTOR no accede a una celula que no lidera -> 403")
    void obtenerCelulaAjenaComoMentorEsRechazado() {
        Celula ajena = celulaExistente();
        when(loadCelulaPort.porId(ajena.id())).thenReturn(Optional.of(ajena));
        assertThatThrownBy(() -> service.obtener(mentor, ajena.id())).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("crear(): cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void crearConAdminSuspendidoFalla() {
        var command = new CrearCelulaCommand(adminSuspendido, "Celula 1", CohorteId.of(UUID.randomUUID()), null);
        assertThatThrownBy(() -> service.crear(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveCelulaPort, never()).save(any());
    }

    @Test
    @DisplayName("actualizar(): cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void actualizarConAdminSuspendidoFalla() {
        var command = new ActualizarCelulaCommand(adminSuspendido, CelulaId.of(UUID.randomUUID()), "Otro", null, true);
        assertThatThrownBy(() -> service.actualizar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveCelulaPort, never()).save(any());
    }

    @Test
    @DisplayName("asignar(mentor): cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void asignarMentorConAdminSuspendidoFalla() {
        var command = new AsignarMentorCelulaCommand(adminSuspendido, CelulaId.of(UUID.randomUUID()), mentor);
        assertThatThrownBy(() -> service.asignar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveCelulaPort, never()).save(any());
    }

    @Test
    @DisplayName("quitar(mentor): cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void quitarMentorConAdminSuspendidoFalla() {
        var command = new QuitarMentorCelulaCommand(adminSuspendido, CelulaId.of(UUID.randomUUID()));
        assertThatThrownBy(() -> service.quitar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveCelulaPort, never()).save(any());
    }

    @Test
    @DisplayName("asignar(aprendiz): cuenta SUSPENDIDA -> 403, nunca delega en `users`")
    void asignarAprendizConAdminSuspendidoFalla() {
        var command = new AsignarAprendizCelulaCommand(adminSuspendido, CelulaId.of(UUID.randomUUID()), trainee);
        assertThatThrownBy(() -> service.asignar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(asignacionCelulaPort, never()).asignarCelula(any(), any(), any());
    }

    @Test
    @DisplayName("quitar(aprendiz): cuenta SUSPENDIDA -> 403, nunca delega en `users`")
    void quitarAprendizConAdminSuspendidoFalla() {
        var command = new QuitarAprendizCelulaCommand(adminSuspendido, trainee);
        assertThatThrownBy(() -> service.quitar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(asignacionCelulaPort, never()).quitarCelula(any(), any());
    }

    @Test
    @DisplayName("programar(): cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void programarSesionConAdminSuspendidoFalla() {
        var command = new ProgramarSesionCelulaCommand(adminSuspendido, CelulaId.of(UUID.randomUUID()), CLOCK.now());
        assertThatThrownBy(() -> service.programar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveCelulaPort, never()).save(any());
    }

    @Test
    @DisplayName("eliminar(): cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void eliminarConAdminSuspendidoFalla() {
        var command = new EliminarCelulaCommand(adminSuspendido, CelulaId.of(UUID.randomUUID()));
        assertThatThrownBy(() -> service.eliminar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(eliminarCelulaPort, never()).eliminar(any());
    }

    @Test
    @DisplayName("listarPorCohorte(): cuenta SUSPENDIDA -> 403")
    void listarPorCohorteConAdminSuspendidoFalla() {
        CohorteId cohorteId = CohorteId.of(UUID.randomUUID());
        assertThatThrownBy(() -> service.listarPorCohorte(adminSuspendido, cohorteId))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("obtener(): cuenta SUSPENDIDA -> 403")
    void obtenerConAdminSuspendidoFalla() {
        CelulaId celulaId = CelulaId.of(UUID.randomUUID());
        assertThatThrownBy(() -> service.obtener(adminSuspendido, celulaId))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("miCelula(): cuenta SUSPENDIDA -> 403")
    void miCelulaConActorSuspendidoFalla() {
        assertThatThrownBy(() -> service.miCelula(adminSuspendido)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("misCompaneros(): cuenta SUSPENDIDA -> 403")
    void misCompanerosConActorSuspendidoFalla() {
        assertThatThrownBy(() -> service.misCompaneros(adminSuspendido)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("dashboard(): cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void dashboardConAdminSuspendidoFalla() {
        assertThatThrownBy(() -> service.dashboard(adminSuspendido)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("mentoresDisponibles(): cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void mentoresDisponiblesConAdminSuspendidoFalla() {
        assertThatThrownBy(() -> service.mentoresDisponibles(adminSuspendido))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("mentores(): cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void mentoresConAdminSuspendidoFalla() {
        assertThatThrownBy(() -> service.mentores(adminSuspendido)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("aprendicesDisponibles(): cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void aprendicesDisponiblesConAdminSuspendidoFalla() {
        assertThatThrownBy(() -> service.aprendicesDisponibles(adminSuspendido))
                .isInstanceOf(NotAuthorizedException.class);
    }
}
