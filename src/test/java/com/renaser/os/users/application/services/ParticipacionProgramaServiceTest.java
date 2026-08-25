package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.participante.ActivateSelfTrackingUseCase.ActivateSelfTrackingCommand;
import com.renaser.os.users.application.ports.in.participante.AssignMentorToTraineeUseCase.AssignMentorCommand;
import com.renaser.os.users.application.ports.in.participante.DeactivateSelfTrackingUseCase.DeactivateSelfTrackingCommand;
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

    private ParticipacionProgramaService service;

    @BeforeEach
    void setUp() {
        service = new ParticipacionProgramaService(new RequireActiveUserGuard(loadUserPort),
                loadParticipacionProgramaPort, saveParticipacionProgramaPort, deleteParticipacionProgramaPort,
                consultarResumenParticipacionPort, loadMentorProfilePort, CLOCK);
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
}
