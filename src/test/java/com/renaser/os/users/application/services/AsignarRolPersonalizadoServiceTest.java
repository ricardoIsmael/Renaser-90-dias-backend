package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.RolDeUsuarioCambiadoEvent;
import com.renaser.os.users.api.RolPersonalizadoCambiadoEvent;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.user.UpdateUserRoleUseCase.UpdateUserRoleCommand;
import com.renaser.os.users.application.ports.out.autenticacion.EnviarEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.SaveCredencialPort;
import com.renaser.os.users.application.ports.out.mentorprofile.LoadMentorProfilePort;
import com.renaser.os.users.application.ports.out.mentorprofile.SaveMentorProfilePort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.rolpersonalizado.CargarRolesPersonalizadosPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado.Datos;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** {@code PATCH /users/{id}/role} con rol personalizado (spec 002, FR-031; D-269). Sin Spring. */
class AsignarRolPersonalizadoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-14T15:00:00Z"));

    private final LoadUserPort loadUserPort = mock(LoadUserPort.class);
    private final SaveUserPort saveUserPort = mock(SaveUserPort.class);
    private final LoadMentorProfilePort perfiles = mock(LoadMentorProfilePort.class);
    private final CargarRolesPersonalizadosPort roles = mock(CargarRolesPersonalizadosPort.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final UserId adminId = UserId.of(UUID.randomUUID());
    private final UserId alquimistaId = UserId.of(UUID.randomUUID());
    private final UserId mentorId = UserId.of(UUID.randomUUID());
    private final UserId cuentaId = UserId.of(UUID.randomUUID());
    private final RolPersonalizado mentorJunior = RolPersonalizado.crear(UUID.randomUUID(),
            new Datos("Mentor junior", null, UserRole.MENTOR, Set.of("muro")), UserId.of(UUID.randomUUID()),
            CLOCK.now());
    private UserAccountService service;

    @BeforeEach
    void armar() {
        service = new UserAccountService(loadUserPort, saveUserPort, perfiles, mock(SaveMentorProfilePort.class),
                mock(LoadParticipacionProgramaPort.class), new RequireActiveUserGuard(loadUserPort),
                mock(SaveCredencialPort.class), mock(EnviarEmailPort.class), mock(PasswordEncoder.class), events,
                CLOCK, mock(IdGenerator.class), mock(TransactionTemplate.class), roles);
        when(saveUserPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(perfiles.byUserId(any())).thenReturn(Optional.empty());
        cuenta(adminId, UserRole.ADMIN);
        cuenta(alquimistaId, UserRole.ALCHEMIST);
        cuenta(mentorId, UserRole.MENTOR);
        cuenta(cuentaId, UserRole.TRAINEE);
        when(roles.porId(mentorJunior.id())).thenReturn(Optional.of(mentorJunior));
    }

    private User cuenta(UserId id, UserRole rol) {
        User user = User.rehydrate(id, new Email(id.value() + "@renaser.test"), rol, UserStatus.ACTIVE, "Persona",
                null, null, null, null);
        when(loadUserPort.byId(id)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    @DisplayName("con rolPersonalizadoId: la cuenta toma su base (se ignora newRole) y se avisan los dos cambios")
    void asignaYAvisa() {
        service.updateRole(new UpdateUserRoleCommand(cuentaId, UserRole.ADMIN, alquimistaId, mentorJunior.id()));

        ArgumentCaptor<User> guardada = ArgumentCaptor.forClass(User.class);
        verify(saveUserPort).save(guardada.capture());
        assertThat(guardada.getValue().role()).isEqualTo(UserRole.MENTOR);
        assertThat(guardada.getValue().rolPersonalizadoId()).isEqualTo(mentorJunior.id());
        verify(events).publishEvent(new RolDeUsuarioCambiadoEvent(cuentaId, UserRole.TRAINEE, UserRole.MENTOR,
                CLOCK.now()));
        verify(events).publishEvent(new RolPersonalizadoCambiadoEvent(mentorJunior.id(), CLOCK.now()));
    }

    @Test
    @DisplayName("sin rolPersonalizadoId se quita el que tuviera y se avisa, aunque el rol base no cambie")
    void quitarloAvisa() {
        User conRol = cuenta(cuentaId, UserRole.MENTOR);
        conRol.asignarRolPersonalizado(mentorJunior, loadUserPort.byId(adminId).orElseThrow());

        service.updateRole(new UpdateUserRoleCommand(cuentaId, UserRole.MENTOR, adminId));

        assertThat(conRol.rolPersonalizadoId()).isNull();
        verify(events, never()).publishEvent(any(RolDeUsuarioCambiadoEvent.class));
        verify(events).publishEvent(new RolPersonalizadoCambiadoEvent(mentorJunior.id(), CLOCK.now()));
    }

    @Test
    @DisplayName("quien no administra roles recibe 403 antes de saber si el rol personalizado existe")
    void noAdministraRoles() {
        assertThatThrownBy(() -> service.updateRole(new UpdateUserRoleCommand(cuentaId, null, mentorId,
                UUID.randomUUID()))).isInstanceOf(NotAuthorizedException.class);

        verifyNoInteractions(roles);
        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("un rol personalizado que no existe es 400; el comando sin ningún rol también")
    void noExiste() {
        UUID inventado = UUID.randomUUID();
        when(roles.porId(inventado)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRole(new UpdateUserRoleCommand(cuentaId, null, adminId, inventado)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no existe");
        assertThatThrownBy(() -> new UpdateUserRoleCommand(cuentaId, null, adminId, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("un ALQUIMISTA no asigna un rol que hereda de ADMIN (403); un ADMIN sí")
    void heredaDeAdmin() {
        RolPersonalizado adminAcotado = RolPersonalizado.crear(UUID.randomUUID(), new Datos("Admin acotado", null,
                UserRole.ADMIN, Set.of("admin")), UserId.of(UUID.randomUUID()), CLOCK.now());
        when(roles.porId(adminAcotado.id())).thenReturn(Optional.of(adminAcotado));

        assertThatThrownBy(() -> service.updateRole(new UpdateUserRoleCommand(cuentaId, null, alquimistaId,
                adminAcotado.id()))).isInstanceOf(NotAuthorizedException.class);
        verify(saveUserPort, never()).save(any());

        service.updateRole(new UpdateUserRoleCommand(cuentaId, null, adminId, adminAcotado.id()));
        verify(saveUserPort).save(any());
    }
}
