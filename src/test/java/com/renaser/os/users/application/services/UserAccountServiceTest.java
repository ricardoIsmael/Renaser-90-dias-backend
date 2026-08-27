package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.user.InviteAndCreateUserUseCase.InviteStaffCommand;
import com.renaser.os.users.application.ports.in.user.InviteAndCreateUserUseCase.InviteUserCommand;
import com.renaser.os.users.application.ports.in.user.UpdateMyProfileUseCase.UpdateMyProfileCommand;
import com.renaser.os.users.application.ports.in.user.UpdateUserRoleUseCase.UpdateUserRoleCommand;
import com.renaser.os.users.application.ports.out.autenticacion.EnviarEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.SaveCredencialPort;
import com.renaser.os.users.application.ports.out.mentorprofile.LoadMentorProfilePort;
import com.renaser.os.users.application.ports.out.mentorprofile.SaveMentorProfilePort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import com.renaser.os.users.domain.model.user.Credencial;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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

/**
 * Regresion del hallazgo de la fase de pruebas de endpoints (2026-08-25): un actor
 * SUSPENDIDO podia seguir invitando usuarios, cambiando roles y editando su propio
 * perfil porque `requireUser(id)` cargaba el actor sin verificar `hasAccess()`. Cubre
 * la capa 3 de defensa en profundidad (CLAUDE.MD §5.3.4/D-11) para los 4 casos de uso
 * de este servicio.
 */
@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    private static final Clock CLOCK = FixedClock.at(Instant.parse("2026-08-25T10:00:00Z"));

    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private SaveUserPort saveUserPort;
    @Mock
    private LoadMentorProfilePort loadMentorProfilePort;
    @Mock
    private SaveMentorProfilePort saveMentorProfilePort;
    @Mock
    private LoadParticipacionProgramaPort loadParticipacionProgramaPort;
    @Mock
    private SaveCredencialPort saveCredencialPort;
    @Mock
    private EnviarEmailPort enviarEmailPort;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private org.springframework.context.ApplicationEventPublisher events;

    private UserAccountService service;

    @BeforeEach
    void setUp() {
        service = new UserAccountService(loadUserPort, saveUserPort, loadMentorProfilePort, saveMentorProfilePort,
                loadParticipacionProgramaPort, new RequireActiveUserGuard(loadUserPort), saveCredencialPort,
                enviarEmailPort, passwordEncoder, events, CLOCK);
        lenient().when(saveUserPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("{bcrypt}hash");
    }

    private static UserId id() {
        return UserId.of(UUID.randomUUID());
    }

    private static User activo(UserId id, UserRole role) {
        return User.rehydrate(id, new Email("test" + id.value() + "@renaser.dev"), role, UserStatus.ACTIVE,
                "Test", null, null, null, null);
    }

    private static User suspendido(UserId id, UserRole role) {
        User user = activo(id, role);
        user.suspend();
        return user;
    }

    @Test
    @DisplayName("BUG-1: un ADMIN SUSPENDIDO no puede cambiar el rol de otro usuario")
    void updateRoleRechazaActorSuspendido() {
        UserId targetId = id();
        UserId actorId = id();
        when(loadUserPort.byId(targetId)).thenReturn(Optional.of(activo(targetId, UserRole.MENTOR)));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(suspendido(actorId, UserRole.ADMIN)));

        assertThatThrownBy(() -> service.updateRole(new UpdateUserRoleCommand(targetId, UserRole.TRAINEE, actorId)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("un ADMIN activo si puede cambiar el rol de otro usuario")
    void updateRoleAceptaActorActivo() {
        UserId targetId = id();
        UserId actorId = id();
        when(loadUserPort.byId(targetId)).thenReturn(Optional.of(activo(targetId, UserRole.MENTOR)));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId, UserRole.ADMIN)));

        service.updateRole(new UpdateUserRoleCommand(targetId, UserRole.TRAINEE, actorId));

        verify(saveUserPort).save(any());
    }

    @Test
    @DisplayName("BUG-2: un ADMIN SUSPENDIDO no puede invitar usuarios nuevos")
    void inviteRechazaActorSuspendido() {
        UserId actorId = id();
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(suspendido(actorId, UserRole.ADMIN)));

        assertThatThrownBy(() -> service.invite(new InviteUserCommand("sb-1", "nuevo@renaser.dev", "Nuevo",
                UserRole.MENTOR, actorId)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("BUG-3: un usuario SUSPENDIDO no puede leer su propio perfil")
    void getMyProfileRechazaUsuarioSuspendido() {
        UserId userId = id();
        when(loadUserPort.byId(userId)).thenReturn(Optional.of(suspendido(userId, UserRole.TRAINEE)));

        assertThatThrownBy(() -> service.getMyProfile(userId)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("BUG-3: un usuario SUSPENDIDO no puede editar su propio perfil")
    void updateMyProfileRechazaUsuarioSuspendido() {
        UserId userId = id();
        when(loadUserPort.byId(userId)).thenReturn(Optional.of(suspendido(userId, UserRole.TRAINEE)));

        assertThatThrownBy(() -> service.updateMyProfile(
                new UpdateMyProfileCommand(userId, "Nuevo Nombre", null, null, null)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("un usuario ACTIVO si puede editar su propio perfil")
    void updateMyProfileAceptaUsuarioActivo() {
        UserId userId = id();
        when(loadUserPort.byId(userId)).thenReturn(Optional.of(activo(userId, UserRole.TRAINEE)));

        service.updateMyProfile(new UpdateMyProfileCommand(userId, "Nuevo Nombre", null, null, null));

        verify(saveUserPort).save(any());
    }

    @Test
    @DisplayName("el comando de perfil propio no tiene campo role/programDay: el compilador lo excluye (§5.3.3)")
    void updateMyProfileCommandNoAceptaRolNiProgreso() {
        assertThat(UpdateMyProfileCommand.class.getRecordComponents()).extracting("name")
                .containsExactly("userId", "fullName", "avatarUrl", "bio", "department");
    }

    @Test
    @DisplayName("gap #6: un ADMIN SUSPENDIDO no puede invitar staff nuevo")
    void inviteStaffRechazaActorSuspendido() {
        UserId actorId = id();
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(suspendido(actorId, UserRole.ADMIN)));

        assertThatThrownBy(() -> service.inviteStaff(
                new InviteStaffCommand("nuevo-mentor@renaser.dev", "Nuevo Mentor", UserRole.MENTOR, actorId)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveUserPort, never()).save(any());
        verify(saveCredencialPort, never()).guardar(any(), any());
        verify(enviarEmailPort, never()).enviarInvitacionStaff(any(), any());
    }

    @Test
    @DisplayName("gap #6: invitar staff genera una contrasena temporal, la hashea, la guarda y la envia por email")
    void inviteStaffGeneraCredencialYNotifica() {
        UserId actorId = id();
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId, UserRole.ADMIN)));

        UserId nuevoId = service.inviteStaff(
                new InviteStaffCommand("nuevo-mentor@renaser.dev", "Nuevo Mentor", UserRole.MENTOR, actorId));

        assertThat(nuevoId).isNotNull();
        verify(saveUserPort).save(any());
        var credencialCaptor = org.mockito.ArgumentCaptor.forClass(Credencial.class);
        verify(saveCredencialPort).guardar(org.mockito.ArgumentMatchers.eq(nuevoId), credencialCaptor.capture());
        assertThat(credencialCaptor.getValue().hash()).isEqualTo("{bcrypt}hash");
        verify(enviarEmailPort).enviarInvitacionStaff(org.mockito.ArgumentMatchers.eq("nuevo-mentor@renaser.dev"),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("BUG-3: un usuario SUSPENDIDO no puede leer su perfil enriquecido")
    void getMyFullProfileRechazaUsuarioSuspendido() {
        UserId userId = id();
        when(loadUserPort.byId(userId)).thenReturn(Optional.of(suspendido(userId, UserRole.TRAINEE)));

        assertThatThrownBy(() -> service.getMyFullProfile(userId)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("hueco #1: sin fila en participantes_programa, traineeProfile es null")
    void getMyFullProfileSinParticipacionTraeTraineeProfileNulo() {
        UserId userId = id();
        when(loadUserPort.byId(userId)).thenReturn(Optional.of(activo(userId, UserRole.MENTOR)));
        when(loadParticipacionProgramaPort.byParticipanteId(userId)).thenReturn(Optional.empty());

        var perfil = service.getMyFullProfile(userId);

        assertThat(perfil.user().id()).isEqualTo(userId);
        assertThat(perfil.traineeProfile()).isNull();
    }

    @Test
    @DisplayName("hueco #1: con fila en participantes_programa, traineeProfile trae el resumen")
    void getMyFullProfileConParticipacionTraeResumen() {
        UserId userId = id();
        when(loadUserPort.byId(userId)).thenReturn(Optional.of(activo(userId, UserRole.TRAINEE)));
        ParticipacionPrograma participacion = ParticipacionPrograma.inscribirTraineeAprobado(userId, CLOCK);
        when(loadParticipacionProgramaPort.byParticipanteId(userId)).thenReturn(Optional.of(participacion));

        var perfil = service.getMyFullProfile(userId);

        assertThat(perfil.traineeProfile()).isNotNull();
        assertThat(perfil.traineeProfile().startDate()).isEqualTo(participacion.fechaInicio());
        assertThat(perfil.traineeProfile().isProgramCompleted()).isFalse();
    }

    @Test
    @DisplayName("gap #6: la invitacion de staff no admite el rol TRAINEE")
    void inviteStaffRechazaRolTrainee() {
        UserId actorId = id();

        assertThatThrownBy(() -> new InviteStaffCommand("aprendiz@renaser.dev", "Aprendiz", UserRole.TRAINEE,
                actorId))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
