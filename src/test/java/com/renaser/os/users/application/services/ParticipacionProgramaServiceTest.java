package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.participante.ActivateSelfTrackingUseCase.ActivateSelfTrackingCommand;
import com.renaser.os.users.application.ports.in.participante.AssignMentorToTraineeUseCase.AssignMentorCommand;
import com.renaser.os.users.application.ports.in.participante.AssignTraineeCellUseCase.AssignTraineeCellCommand;
import com.renaser.os.users.application.ports.in.participante.DeactivateSelfTrackingUseCase.DeactivateSelfTrackingCommand;
import com.renaser.os.users.application.ports.in.participante.GetTraineeDetailUseCase.GetTraineeDetailCommand;
import com.renaser.os.users.application.ports.in.participante.ListTraineesUseCase.ListTraineesCommand;
import com.renaser.os.users.application.ports.in.participante.RemoveTraineeCellUseCase.RemoveTraineeCellCommand;
import com.renaser.os.users.application.ports.in.participante.SetTraineeProgramDayUseCase.SetProgramDayCommand;
import com.renaser.os.users.application.ports.in.participante.UpdateTraineeProfileUseCase.UpdateTraineeProfileCommand;
import com.renaser.os.users.application.ports.out.mentorprofile.LoadMentorProfilePort;
import com.renaser.os.users.application.ports.out.participante.ConsultarResumenParticipacionPort;
import com.renaser.os.users.application.ports.out.participante.DeleteParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.mentorprofile.MentorProfile;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipacionProgramaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private LoadParticipacionProgramaPort loadParticipacionProgramaPort;
    @Mock
    private SaveParticipacionProgramaPort saveParticipacionProgramaPort;
    @Mock
    private DeleteParticipacionProgramaPort deleteParticipacionProgramaPort;
    @Mock
    private ConsultarResumenParticipacionPort consultarResumenParticipacionPort;
    @Mock
    private LoadMentorProfilePort loadMentorProfilePort;
    @Mock
    private com.renaser.os.users.application.ports.out.ajustediaprograma.SaveAjusteDiaProgramaPort
            saveAjusteDiaProgramaPort;
    @Mock
    private com.renaser.os.users.application.ports.out.ajustediaprograma.LoadUltimoAjusteDiaProgramaPort
            loadUltimoAjusteDiaProgramaPort;

    @Mock
    private com.renaser.os.users.application.ports.in.emergencia.ResolverEmergenciaAlCambiarDiaUseCase
            resolverEmergencia;

    private ParticipacionProgramaService service;

    @BeforeEach
    void setUp() {
        service = new ParticipacionProgramaService(new RequireActiveUserGuard(loadUserPort),
                loadParticipacionProgramaPort, saveParticipacionProgramaPort, deleteParticipacionProgramaPort,
                consultarResumenParticipacionPort, loadMentorProfilePort, loadUserPort,
                new RequireAdminGuard(loadUserPort), saveAjusteDiaProgramaPort, loadUltimoAjusteDiaProgramaPort,
                UUID::randomUUID, CLOCK, resolverEmergencia);
    }

    private User usuario(UserId id, UserRole role, UserStatus status) {
        return User.rehydrate(id, new Email(id + "@renaser.com"), role, status, "Fixture " + id, null, null, null,
                null);
    }

    // ─── activate ───────────────────────────────────────────────────────────

    @Test
    void unMentorActivaSuSeguimientoPersonalPorPrimeraVez() {
        UserId mentorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(mentorId)).thenReturn(Optional.of(usuario(mentorId, UserRole.MENTOR, UserStatus.ACTIVE)));
        when(loadParticipacionProgramaPort.byParticipanteId(mentorId)).thenReturn(Optional.empty());
        when(saveParticipacionProgramaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ParticipacionPrograma resultado = service.activate(new ActivateSelfTrackingCommand(mentorId));

        assertThat(resultado.diaPrograma()).isEqualTo(1);
        assertThat(resultado.participanteId()).isEqualTo(mentorId);
    }

    @Test
    void activarDeVueltaCuandoYaEstaInscriptoTira409() {
        UserId mentorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(mentorId)).thenReturn(Optional.of(usuario(mentorId, UserRole.MENTOR, UserStatus.ACTIVE)));
        when(loadParticipacionProgramaPort.byParticipanteId(mentorId))
                .thenReturn(Optional.of(ParticipacionPrograma.activarSeguimientoPersonal(mentorId, CLOCK)));

        assertThatThrownBy(() -> service.activate(new ActivateSelfTrackingCommand(mentorId)))
                .isInstanceOf(IllegalStateException.class);

        verify(saveParticipacionProgramaPort, never()).save(any());
    }

    @Test
    void unAprendizNoPuedeActivarSeguimientoPersonalOpcional() {
        UserId traineeId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(traineeId))
                .thenReturn(Optional.of(usuario(traineeId, UserRole.TRAINEE, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.activate(new ActivateSelfTrackingCommand(traineeId)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveParticipacionProgramaPort, never()).save(any());
    }

    /** Test de autorizacion negativa (CLAUDE.MD §0.3): SUSPENDED recibe 403 aunque su rol
     * si tenga permiso, porque el token/cuenta ya no es de confianza. */
    @Test
    void unMentorSuspendidoRecibeNotAuthorizedAunqueSuRolTengaPermiso() {
        UserId mentorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(mentorId))
                .thenReturn(Optional.of(usuario(mentorId, UserRole.MENTOR, UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> service.activate(new ActivateSelfTrackingCommand(mentorId)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveParticipacionProgramaPort, never()).save(any());
    }

    // ─── deactivate ─────────────────────────────────────────────────────────

    @Test
    void desactivarBorraLaParticipacionYDevuelveTrue() {
        UserId mentorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(mentorId)).thenReturn(Optional.of(usuario(mentorId, UserRole.MENTOR, UserStatus.ACTIVE)));
        when(deleteParticipacionProgramaPort.deleteByParticipanteId(mentorId)).thenReturn(true);

        boolean resultado = service.deactivate(new DeactivateSelfTrackingCommand(mentorId));

        assertThat(resultado).isTrue();
    }

    @Test
    void desactivarSinNadaQueBorrarEsIdempotenteYDevuelveFalse() {
        UserId mentorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(mentorId)).thenReturn(Optional.of(usuario(mentorId, UserRole.MENTOR, UserStatus.ACTIVE)));
        when(deleteParticipacionProgramaPort.deleteByParticipanteId(mentorId)).thenReturn(false);

        boolean resultado = service.deactivate(new DeactivateSelfTrackingCommand(mentorId));

        assertThat(resultado).isFalse();
    }

    @Test
    void unAprendizNoPuedeDesactivarSeguimientoPersonalOpcional() {
        UserId traineeId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(traineeId))
                .thenReturn(Optional.of(usuario(traineeId, UserRole.TRAINEE, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.deactivate(new DeactivateSelfTrackingCommand(traineeId)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(deleteParticipacionProgramaPort, never()).deleteByParticipanteId(any());
    }

    // ─── assignMentor ───────────────────────────────────────────────────────

    @Test
    void unAdminAsignaMentorAUnParticipanteInscripto() {
        UserId adminId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        UserId mentorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(adminId)).thenReturn(Optional.of(usuario(adminId, UserRole.ADMIN, UserStatus.ACTIVE)));
        when(loadMentorProfilePort.byUserId(mentorId))
                .thenReturn(Optional.of(MentorProfile.create(mentorId, CLOCK)));
        when(loadParticipacionProgramaPort.byParticipanteId(traineeId))
                .thenReturn(Optional.of(ParticipacionPrograma.activarSeguimientoPersonal(traineeId, CLOCK)));
        when(saveParticipacionProgramaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.assignMentor(new AssignMentorCommand(adminId, traineeId, mentorId));

        verify(saveParticipacionProgramaPort).save(any());
    }

    /** Test de autorizacion negativa (CLAUDE.MD §0.3): un rol sin permiso (MENTOR, no
     * ADMIN/ALCHEMIST) recibe 403 al intentar reasignar el mentor de otro participante. */
    @Test
    void unMentorNoPuedeAsignarMentorAOtroParticipante() {
        UserId mentorActorId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        UserId nuevoMentorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(mentorActorId))
                .thenReturn(Optional.of(usuario(mentorActorId, UserRole.MENTOR, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.assignMentor(new AssignMentorCommand(mentorActorId, traineeId, nuevoMentorId)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveParticipacionProgramaPort, never()).save(any());
    }

    @Test
    void asignarUnMentorSinPerfilDeMentorTiraNoSuchElement() {
        UserId adminId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        UserId mentorSinPerfilId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(adminId)).thenReturn(Optional.of(usuario(adminId, UserRole.ADMIN, UserStatus.ACTIVE)));
        when(loadMentorProfilePort.byUserId(mentorSinPerfilId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.assignMentor(new AssignMentorCommand(adminId, traineeId, mentorSinPerfilId)))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void asignarMentorAUnParticipanteNoInscriptoTiraNoSuchElement() {
        UserId adminId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        UserId mentorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(adminId)).thenReturn(Optional.of(usuario(adminId, UserRole.ADMIN, UserStatus.ACTIVE)));
        when(loadMentorProfilePort.byUserId(mentorId))
                .thenReturn(Optional.of(MentorProfile.create(mentorId, CLOCK)));
        when(loadParticipacionProgramaPort.byParticipanteId(traineeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignMentor(new AssignMentorCommand(adminId, traineeId, mentorId)))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    // ─── Finder ─────────────────────────────────────────────────────────────

    @Test
    void deParticipanteDelegaAlPuertoDeResumen() {
        UserId id = UserId.of(UUID.randomUUID());
        when(consultarResumenParticipacionPort.resumenDe(id)).thenReturn(Optional.empty());

        assertThat(service.deParticipante(id)).isEmpty();
    }

    // ─── panel admin de aprendices (gap #7) ────────────────────────────────

    @Test
    void listarTraineesRechazaActorNoAdmin() {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuario(actorId, UserRole.MENTOR, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.listar(new ListTraineesCommand(actorId, 0, 20)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void listarTraineesAceptaAdminActivo() {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuario(actorId, UserRole.ADMIN, UserStatus.ACTIVE)));
        when(consultarResumenParticipacionPort.listarAprendices(0, 20, null, false)).thenReturn(java.util.List.of());
        when(consultarResumenParticipacionPort.contarAprendices(null, false)).thenReturn(0L);

        var pagina = service.listar(new ListTraineesCommand(actorId, 0, 20));

        assertThat(pagina.total()).isZero();
    }

    @Test
    void obtenerDetalleRechazaTraineeInexistenteAntesDeChequearElActor() {
        UserId actorId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(traineeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener(new GetTraineeDetailCommand(actorId, traineeId)))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void obtenerDetalleRechazaActorNoAdmin() {
        UserId actorId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(traineeId)).thenReturn(Optional.of(usuario(traineeId, UserRole.TRAINEE,
                UserStatus.ACTIVE)));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuario(actorId, UserRole.MENTOR,
                UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.obtener(new GetTraineeDetailCommand(actorId, traineeId)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void fijarDiaRechazaParticipanteNoInscriptoAntesDeChequearElActor() {
        UserId actorId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        when(loadParticipacionProgramaPort.byParticipanteId(traineeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fijarDia(new SetProgramDayCommand(actorId, traineeId, 10)))
                .isInstanceOf(java.util.NoSuchElementException.class);

        verify(saveParticipacionProgramaPort, never()).save(any());
    }

    @Test
    void fijarDiaRechazaActorSuspendido() {
        UserId actorId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        when(loadParticipacionProgramaPort.byParticipanteId(traineeId))
                .thenReturn(Optional.of(ParticipacionPrograma.inscribirTraineeAprobado(traineeId, CLOCK)));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuario(actorId, UserRole.ADMIN,
                UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> service.fijarDia(new SetProgramDayCommand(actorId, traineeId, 10)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveParticipacionProgramaPort, never()).save(any());
    }

    @Test
    void fijarDiaAdminActivoFijaElDiaExacto() {
        UserId actorId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        // Antes usaba `inscribirTraineeAprobado` (sin Dia 1): desde D-195 ese caso es un 409.
        when(loadParticipacionProgramaPort.byParticipanteId(traineeId))
                .thenReturn(Optional.of(ParticipacionPrograma.activarSeguimientoPersonal(traineeId, CLOCK)));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuario(actorId, UserRole.ADMIN,
                UserStatus.ACTIVE)));
        when(saveParticipacionProgramaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.fijarDia(new SetProgramDayCommand(actorId, traineeId, 45));

        var captor = org.mockito.ArgumentCaptor.forClass(ParticipacionPrograma.class);
        verify(saveParticipacionProgramaPort).save(captor.capture());
        assertThat(captor.getValue().diaPrograma()).isEqualTo(45);
        // D-244: el pedido de emergencia abierto queda resuelto con el dia aplicado, en la misma transaccion.
        verify(resolverEmergencia).alCambiarDia(traineeId, actorId, 45);
    }

    /**
     * D-195. Contra el codigo viejo: respondia 204, guardaba el dia sin el ajuste y escribia la
     * bitacora; al llegar el Dia 1 el barrido lo devolvia a 1 sin aviso.
     */
    @Test
    void fijarDiaAntesDelDiaUnoEsConflictoYNoEscribeNadaNiEnLaBitacora() {
        UserId actorId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        when(loadParticipacionProgramaPort.byParticipanteId(traineeId))
                .thenReturn(Optional.of(ParticipacionPrograma.inscribirTraineeAprobado(traineeId, CLOCK)));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuario(actorId, UserRole.ADMIN,
                UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.fijarDia(new SetProgramDayCommand(actorId, traineeId, 45, "Viaje")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Esta persona todavía no empezó su Día 1: el día se puede ajustar desde que empieza");

        verify(saveParticipacionProgramaPort, never()).save(any());
        verify(saveAjusteDiaProgramaPort, never()).save(any());
        verify(resolverEmergencia, never()).alCambiarDia(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    /**
     * Regla 03, madrugada UTC: eligio empezar el 27; a las 04:30 UTC del 27 en Lima todavia es el
     * 26, asi que todavia no empezo. Con la fecha del servidor se habria tomado como su Dia 1.
     */
    @Test
    void fijarDiaEnLaMadrugadaUtcDeSuDiaUnoTodaviaEsConflicto() {
        var servicio = servicioCon(FixedClock.at(Instant.parse("2026-09-27T04:30:00Z")));
        UserId actorId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        var arrancaEl27 = ParticipacionPrograma.rehydrate(traineeId, null, null, 0,
                com.renaser.os.users.api.FasePrograma.paraDiaPrograma(0), java.time.LocalDate.of(2026, 9, 27),
                Instant.parse("2026-09-25T15:00:00Z"), java.time.ZoneId.of("America/Lima"), false, 0,
                Instant.parse("2026-09-20T15:00:00Z"), Instant.parse("2026-09-25T15:00:00Z"), null, null, null,
                null, 0);
        when(loadParticipacionProgramaPort.byParticipanteId(traineeId)).thenReturn(Optional.of(arrancaEl27));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuario(actorId, UserRole.ADMIN,
                UserStatus.ACTIVE)));

        assertThatThrownBy(() -> servicio.fijarDia(new SetProgramDayCommand(actorId, traineeId, 5, "x")))
                .isInstanceOf(IllegalStateException.class);

        verify(saveAjusteDiaProgramaPort, never()).save(any());
    }

    @Test
    void elComandoRechazaElCeroYElNoventa() {
        UserId actorId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> new SetProgramDayCommand(actorId, traineeId, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("entre 1 y 89");
        assertThatThrownBy(() -> new SetProgramDayCommand(actorId, traineeId, 90))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("entre 1 y 89");
    }

    // --- bitacora de ajustes (D-82) -------------------------------------

    /**
     * El agujero que cierra D-82: antes se podia mover a alguien del dia 40 al 34 y no
     * quedaba registro de quien ni por que.
     */
    @Test
    void fijarDiaDejaConstanciaDeQuienLoMovioYPorQue() {
        UserId actorId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        var participacion = ParticipacionPrograma.activarSeguimientoPersonal(traineeId, CLOCK);
        when(loadParticipacionProgramaPort.byParticipanteId(traineeId)).thenReturn(Optional.of(participacion));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuario(actorId, UserRole.ADMIN,
                UserStatus.ACTIVE)));
        when(saveParticipacionProgramaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.fijarDia(new SetProgramDayCommand(actorId, traineeId, 34, "Viaje, aviso al volver"));

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.renaser.os.users.domain.model.ajustediaprograma.AjusteDiaPrograma.class);
        verify(saveAjusteDiaProgramaPort).save(captor.capture());
        var ajuste = captor.getValue();
        assertThat(ajuste.participanteId()).isEqualTo(traineeId);
        assertThat(ajuste.ajustadoPor()).isEqualTo(actorId);
        assertThat(ajuste.diaAnterior()).isEqualTo(1);
        assertThat(ajuste.diaNuevo()).isEqualTo(34);
        assertThat(ajuste.motivo()).isEqualTo("Viaje, aviso al volver");
    }

    /**
     * E-319. A las 00:03 de Lima (05:03 UTC) el barrido del reloj (minuto :05) todavia no
     * materializo el dia de hoy: la columna dice 25 y el aprendiz vive su dia 26 — que es lo que
     * el panel muestra, porque la lectura deriva. La bitacora tiene que decir "de 26 a 30", no
     * "de 25 a 30". Contra el codigo viejo esta prueba falla con {@code expected: 26 but was: 25}.
     */
    @Test
    void laBitacoraAnotaElDiaQueElAprendizEstaViviendoAunqueElBarridoNoLoHayaMaterializado() {
        FixedClock pasadaLaMedianocheDeLima = FixedClock.at(Instant.parse("2026-09-26T05:03:00Z"));
        var servicio = servicioCon(pasadaLaMedianocheDeLima);
        UserId actorId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        var materializadaAyer = ParticipacionPrograma.rehydrate(traineeId, null, null, 25,
                com.renaser.os.users.api.FasePrograma.paraDiaPrograma(25), java.time.LocalDate.of(2026, 9, 1),
                Instant.parse("2026-08-31T15:00:00Z"), java.time.ZoneId.of("America/Lima"), false, 0,
                Instant.parse("2026-08-31T15:00:00Z"), Instant.parse("2026-09-25T05:05:00Z"), null, null, null,
                java.time.LocalDate.of(2026, 9, 25), 0);
        when(loadParticipacionProgramaPort.byParticipanteId(traineeId)).thenReturn(Optional.of(materializadaAyer));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuario(actorId, UserRole.ADMIN,
                UserStatus.ACTIVE)));
        when(saveParticipacionProgramaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        servicio.fijarDia(new SetProgramDayCommand(actorId, traineeId, 30, "Se adelanta"));

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.renaser.os.users.domain.model.ajustediaprograma.AjusteDiaPrograma.class);
        verify(saveAjusteDiaProgramaPort).save(captor.capture());
        assertThat(captor.getValue().diaAnterior()).isEqualTo(26);
        assertThat(captor.getValue().diaNuevo()).isEqualTo(30);
        assertThat(captor.getValue().diasAjusteNuevo()).isEqualTo(-4);
    }

    /**
     * La otra cara de E-319, con el reloj en la hora que esconde los bugs de zona (regla 03): a
     * las 04:30 UTC del 27 en Lima todavia es el 26. El dia anterior es 26 (el de Lima), no 27
     * (el que daria la fecha UTC del servidor).
     */
    @Test
    void laBitacoraUsaElDiaDeLaZonaDelAprendizNoLaFechaDelServidor() {
        FixedClock nocheDelVeintiseisEnLima = FixedClock.at(Instant.parse("2026-09-27T04:30:00Z"));
        var servicio = servicioCon(nocheDelVeintiseisEnLima);
        UserId actorId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        var participacion = ParticipacionPrograma.rehydrate(traineeId, null, null, 26,
                com.renaser.os.users.api.FasePrograma.paraDiaPrograma(26), java.time.LocalDate.of(2026, 9, 1),
                Instant.parse("2026-08-31T15:00:00Z"), java.time.ZoneId.of("America/Lima"), false, 0,
                Instant.parse("2026-08-31T15:00:00Z"), Instant.parse("2026-09-26T05:05:00Z"), null, null, null,
                java.time.LocalDate.of(2026, 9, 26), 0);
        when(loadParticipacionProgramaPort.byParticipanteId(traineeId)).thenReturn(Optional.of(participacion));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuario(actorId, UserRole.ADMIN,
                UserStatus.ACTIVE)));
        when(saveParticipacionProgramaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        servicio.fijarDia(new SetProgramDayCommand(actorId, traineeId, 20, "Viaje"));

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.renaser.os.users.domain.model.ajustediaprograma.AjusteDiaPrograma.class);
        verify(saveAjusteDiaProgramaPort).save(captor.capture());
        assertThat(captor.getValue().diaAnterior()).isEqualTo(26);
        assertThat(captor.getValue().diasAjusteNuevo()).isEqualTo(6);
    }

    private ParticipacionProgramaService servicioCon(FixedClock reloj) {
        return new ParticipacionProgramaService(new RequireActiveUserGuard(loadUserPort),
                loadParticipacionProgramaPort, saveParticipacionProgramaPort, deleteParticipacionProgramaPort,
                consultarResumenParticipacionPort, loadMentorProfilePort, loadUserPort,
                new RequireAdminGuard(loadUserPort), saveAjusteDiaProgramaPort, loadUltimoAjusteDiaProgramaPort,
                UUID::randomUUID, reloj, resolverEmergencia);
    }

    /** Un ajuste rechazado no puede dejar rastro en la bitacora. */
    @Test
    void fijarDiaRechazadoNoEscribeEnLaBitacora() {
        UserId actorId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        when(loadParticipacionProgramaPort.byParticipanteId(traineeId))
                .thenReturn(Optional.of(ParticipacionPrograma.inscribirTraineeAprobado(traineeId, CLOCK)));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuario(actorId, UserRole.ADMIN,
                UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> service.fijarDia(new SetProgramDayCommand(actorId, traineeId, 34, "x")))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveAjusteDiaProgramaPort, never()).save(any());
    }

    // ─── assign/remove celula (panel admin de aprendices, gap #25) ─────────

    @Test
    void assignCelulaRechazaParticipanteNoInscriptoAntesDeChequearElActor() {
        UserId actorId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        UUID celulaId = UUID.randomUUID();
        when(loadParticipacionProgramaPort.byParticipanteId(traineeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(new AssignTraineeCellCommand(actorId, traineeId, celulaId)))
                .isInstanceOf(java.util.NoSuchElementException.class);

        verify(saveParticipacionProgramaPort, never()).save(any());
    }

    /** Test de autorizacion negativa (CLAUDE.MD §0.3): rol sin permiso. */
    @Test
    void assignCelulaRechazaActorNoAdmin() {
        UserId actorId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        UUID celulaId = UUID.randomUUID();
        when(loadParticipacionProgramaPort.byParticipanteId(traineeId))
                .thenReturn(Optional.of(ParticipacionPrograma.inscribirTraineeAprobado(traineeId, CLOCK)));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuario(actorId, UserRole.MENTOR, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.assign(new AssignTraineeCellCommand(actorId, traineeId, celulaId)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveParticipacionProgramaPort, never()).save(any());
    }

    /** Test de autorizacion negativa (CLAUDE.MD §0.3): SUSPENDED con token valido. */
    @Test
    void assignCelulaRechazaActorSuspendido() {
        UserId actorId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        UUID celulaId = UUID.randomUUID();
        when(loadParticipacionProgramaPort.byParticipanteId(traineeId))
                .thenReturn(Optional.of(ParticipacionPrograma.inscribirTraineeAprobado(traineeId, CLOCK)));
        when(loadUserPort.byId(actorId))
                .thenReturn(Optional.of(usuario(actorId, UserRole.ADMIN, UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> service.assign(new AssignTraineeCellCommand(actorId, traineeId, celulaId)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveParticipacionProgramaPort, never()).save(any());
    }

    @Test
    void assignCelulaAdminActivoAsignaLaCelula() {
        UserId actorId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        UUID celulaId = UUID.randomUUID();
        when(loadParticipacionProgramaPort.byParticipanteId(traineeId))
                .thenReturn(Optional.of(ParticipacionPrograma.inscribirTraineeAprobado(traineeId, CLOCK)));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuario(actorId, UserRole.ADMIN, UserStatus.ACTIVE)));
        when(saveParticipacionProgramaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.assign(new AssignTraineeCellCommand(actorId, traineeId, celulaId));

        var captor = org.mockito.ArgumentCaptor.forClass(ParticipacionPrograma.class);
        verify(saveParticipacionProgramaPort).save(captor.capture());
        assertThat(captor.getValue().celulaId()).isEqualTo(celulaId);
    }

    @Test
    void removeCelulaRechazaParticipanteNoInscriptoAntesDeChequearElActor() {
        UserId actorId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        when(loadParticipacionProgramaPort.byParticipanteId(traineeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(new RemoveTraineeCellCommand(actorId, traineeId)))
                .isInstanceOf(java.util.NoSuchElementException.class);

        verify(saveParticipacionProgramaPort, never()).save(any());
    }

    /** Test de autorizacion negativa (CLAUDE.MD §0.3): rol sin permiso. */
    @Test
    void removeCelulaRechazaActorNoAdmin() {
        UserId actorId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        ParticipacionPrograma existente = ParticipacionPrograma.inscribirTraineeAprobado(traineeId, CLOCK);
        existente.asignarCelula(UUID.randomUUID(), CLOCK);
        when(loadParticipacionProgramaPort.byParticipanteId(traineeId)).thenReturn(Optional.of(existente));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuario(actorId, UserRole.MENTOR, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.remove(new RemoveTraineeCellCommand(actorId, traineeId)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveParticipacionProgramaPort, never()).save(any());
    }

    @Test
    void removeCelulaAdminActivoQuitaLaCelula() {
        UserId actorId = UserId.of(UUID.randomUUID());
        UserId traineeId = UserId.of(UUID.randomUUID());
        ParticipacionPrograma existente = ParticipacionPrograma.inscribirTraineeAprobado(traineeId, CLOCK);
        existente.asignarCelula(UUID.randomUUID(), CLOCK);
        when(loadParticipacionProgramaPort.byParticipanteId(traineeId)).thenReturn(Optional.of(existente));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuario(actorId, UserRole.ADMIN, UserStatus.ACTIVE)));
        when(saveParticipacionProgramaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.remove(new RemoveTraineeCellCommand(actorId, traineeId));

        var captor = org.mockito.ArgumentCaptor.forClass(ParticipacionPrograma.class);
        verify(saveParticipacionProgramaPort).save(captor.capture());
        assertThat(captor.getValue().celulaId()).isNull();
    }

    // ─── updateMyTraineeProfile (hueco #1, U-05) ───────────────────────────

    @Test
    void actualizarMiPerfilDeTraineeCambiaElRetoPersonal() {
        UserId traineeId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(traineeId))
                .thenReturn(Optional.of(usuario(traineeId, UserRole.TRAINEE, UserStatus.ACTIVE)));
        when(loadParticipacionProgramaPort.byParticipanteId(traineeId))
                .thenReturn(Optional.of(ParticipacionPrograma.inscribirTraineeAprobado(traineeId, CLOCK)));
        when(saveParticipacionProgramaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resultado = service.updateMyTraineeProfile(
                new UpdateTraineeProfileCommand(traineeId, "Correr una maraton"));

        assertThat(resultado.nombreRetoPersonal()).isEqualTo("Correr una maraton");
    }

    @Test
    void actualizarMiPerfilDeTraineeSinCambioDeNombreNoLoBorra() {
        UserId traineeId = UserId.of(UUID.randomUUID());
        ParticipacionPrograma existente = ParticipacionPrograma.inscribirTraineeAprobado(traineeId, CLOCK);
        existente.renombrarRetoPersonal("Ya tenia un reto", CLOCK);
        when(loadUserPort.byId(traineeId))
                .thenReturn(Optional.of(usuario(traineeId, UserRole.TRAINEE, UserStatus.ACTIVE)));
        when(loadParticipacionProgramaPort.byParticipanteId(traineeId)).thenReturn(Optional.of(existente));
        when(saveParticipacionProgramaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resultado = service.updateMyTraineeProfile(new UpdateTraineeProfileCommand(traineeId, null));

        assertThat(resultado.nombreRetoPersonal()).isEqualTo("Ya tenia un reto");
    }

    @Test
    void actualizarMiPerfilDeTraineeSinFilaDeProgramaTiraNoSuchElement() {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuario(actorId, UserRole.MENTOR, UserStatus.ACTIVE)));
        when(loadParticipacionProgramaPort.byParticipanteId(actorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMyTraineeProfile(new UpdateTraineeProfileCommand(actorId, "algo")))
                .isInstanceOf(java.util.NoSuchElementException.class);

        verify(saveParticipacionProgramaPort, never()).save(any());
    }

    /** Test de autorizacion negativa (CLAUDE.MD §0.3). */
    @Test
    void actualizarMiPerfilDeTraineeRechazaActorSuspendido() {
        UserId traineeId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(traineeId))
                .thenReturn(Optional.of(usuario(traineeId, UserRole.TRAINEE, UserStatus.SUSPENDED)));

        assertThatThrownBy(
                () -> service.updateMyTraineeProfile(new UpdateTraineeProfileCommand(traineeId, "algo")))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveParticipacionProgramaPort, never()).save(any());
    }

    /** Self-only por diseño: el comando no tiene campo "traineeId" — solo edita al propio actor. */
    @Test
    void updateTraineeProfileCommandEsSelfOnly() {
        assertThat(UpdateTraineeProfileCommand.class.getRecordComponents()).extracting("name")
                .containsExactly("actorId", "personalChallengeName");
    }
}
