package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.user.InviteAndCreateUserUseCase.InviteUserCommand;
import com.renaser.os.users.application.ports.in.user.UpdateMyProfileUseCase.UpdateMyProfileCommand;
import com.renaser.os.users.application.ports.in.user.UpdateUserRoleUseCase.UpdateUserRoleCommand;
import com.renaser.os.users.application.ports.out.mentorprofile.LoadMentorProfilePort;
import com.renaser.os.users.application.ports.out.mentorprofile.SaveMentorProfilePort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
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
    private org.springframework.context.ApplicationEventPublisher events;

    private UserAccountService service;

    @BeforeEach
    void setUp() {
        service = new UserAccountService(loadUserPort, saveUserPort, loadMentorProfilePort, saveMentorProfilePort,
                new RequireActiveUserGuard(loadUserPort), events, CLOCK);
        lenient().when(saveUserPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
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
}
