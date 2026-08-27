package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.admin.ListStaffUseCase.ListStaffCommand;
import com.renaser.os.users.application.ports.in.admin.UpdateStaffProfileUseCase.UpdateStaffProfileCommand;
import com.renaser.os.users.application.ports.in.admin.UpdateUserStatusUseCase.UpdateUserStatusCommand;
import com.renaser.os.users.application.ports.in.autenticacion.CerrarTodasLasSesionesUseCase;
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

import java.util.NoSuchElementException;
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
 * Panel admin de staff (gap #6). El orden de los guards importa (docs/BITACORA_ERRORES.md
 * E-42): el target se carga PRIMERO (404 si no existe), el actor se verifica DESPUES,
 * siempre con un 403 (nunca un 404 con mensaje de "actor no encontrado" para un actor
 * invalido).
 */
@ExtendWith(MockitoExtension.class)
class StaffAdminServiceTest {

    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private SaveUserPort saveUserPort;
    @Mock
    private CerrarTodasLasSesionesUseCase cerrarTodasLasSesionesUseCase;

    private StaffAdminService service;

    @BeforeEach
    void setUp() {
        service = new StaffAdminService(loadUserPort, saveUserPort, new RequireAdminGuard(loadUserPort),
                cerrarTodasLasSesionesUseCase);
        lenient().when(saveUserPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static UserId id() {
        return UserId.of(UUID.randomUUID());
    }

    private static User activo(UserId id, UserRole role) {
        return User.rehydrate(id, new Email("staff" + id.value() + "@renaser.dev"), role, UserStatus.ACTIVE,
                "Staff", null, null, null, null);
    }

    private static User suspendido(UserId id, UserRole role) {
        User user = activo(id, role);
        user.suspend();
        return user;
    }

    @Test
    @DisplayName("listar: un actor no-ADMIN es rechazado con 403")
    void listarRechazaActorSinPermiso() {
        UserId actorId = id();
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId, UserRole.MENTOR)));

        assertThatThrownBy(() -> service.listar(new ListStaffCommand(actorId, null, null, 0, 20)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("listar: un actor ADMIN activo si puede listar staff")
    void listarAceptaAdminActivo() {
        UserId actorId = id();
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId, UserRole.ADMIN)));
        when(loadUserPort.byRoles(any(), any(), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(20))).thenReturn(java.util.List.of());
        when(loadUserPort.countByRoles(any(), any())).thenReturn(0L);

        var pagina = service.listar(new ListStaffCommand(actorId, null, null, 0, 20));

        assertThat(pagina.total()).isZero();
    }

    @Test
    @DisplayName("updateStatus: si el target no existe, 404 sin importar si el actor es valido")
    void updateStatusRechazaTargetInexistenteAntesQueElActor() {
        UserId actorId = id();
        UserId targetId = id();
        when(loadUserPort.byId(targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(
                new UpdateUserStatusCommand(actorId, targetId, UserStatus.SUSPENDED)))
                .isInstanceOf(NoSuchElementException.class);

        verify(saveUserPort, never()).save(any());
        verify(cerrarTodasLasSesionesUseCase, never()).cerrarTodas(any());
    }

    @Test
    @DisplayName("updateStatus: un actor SUSPENDIDO no puede suspender a otro staff (403, no 404)")
    void updateStatusRechazaActorSuspendido() {
        UserId actorId = id();
        UserId targetId = id();
        when(loadUserPort.byId(targetId)).thenReturn(Optional.of(activo(targetId, UserRole.MENTOR)));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(suspendido(actorId, UserRole.ADMIN)));

        assertThatThrownBy(() -> service.updateStatus(
                new UpdateUserStatusCommand(actorId, targetId, UserStatus.SUSPENDED)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("updateStatus: suspender a un staff revoca todas sus sesiones (D-49/MODULO_AUTH.md §7.4)")
    void updateStatusSuspenderRevocaSesiones() {
        UserId actorId = id();
        UserId targetId = id();
        when(loadUserPort.byId(targetId)).thenReturn(Optional.of(activo(targetId, UserRole.MENTOR)));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId, UserRole.ADMIN)));

        service.updateStatus(new UpdateUserStatusCommand(actorId, targetId, UserStatus.SUSPENDED));

        verify(cerrarTodasLasSesionesUseCase).cerrarTodas(targetId);
    }

    @Test
    @DisplayName("updateStatus: reactivar NO revoca sesiones")
    void updateStatusReactivarNoRevocaSesiones() {
        UserId actorId = id();
        UserId targetId = id();
        when(loadUserPort.byId(targetId)).thenReturn(Optional.of(suspendido(targetId, UserRole.MENTOR)));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId, UserRole.ADMIN)));

        service.updateStatus(new UpdateUserStatusCommand(actorId, targetId, UserStatus.ACTIVE));

        verify(cerrarTodasLasSesionesUseCase, never()).cerrarTodas(any());
    }

    @Test
    @DisplayName("updateStaffProfile: target inexistente da 404")
    void updateStaffProfileRechazaTargetInexistente() {
        UserId actorId = id();
        UserId targetId = id();
        when(loadUserPort.byId(targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStaffProfile(
                new UpdateStaffProfileCommand(actorId, targetId, "Nuevo Nombre", null, null, null)))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("updateStaffProfile: un actor SUSPENDIDO no puede editar a otro staff")
    void updateStaffProfileRechazaActorSuspendido() {
        UserId actorId = id();
        UserId targetId = id();
        when(loadUserPort.byId(targetId)).thenReturn(Optional.of(activo(targetId, UserRole.MENTOR)));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(suspendido(actorId, UserRole.ADMIN)));

        assertThatThrownBy(() -> service.updateStaffProfile(
                new UpdateStaffProfileCommand(actorId, targetId, "Nuevo Nombre", null, null, null)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("updateStaffProfile: un ADMIN activo si puede editar a otro staff")
    void updateStaffProfileAceptaAdminActivo() {
        UserId actorId = id();
        UserId targetId = id();
        when(loadUserPort.byId(targetId)).thenReturn(Optional.of(activo(targetId, UserRole.MENTOR)));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId, UserRole.ADMIN)));

        service.updateStaffProfile(new UpdateStaffProfileCommand(actorId, targetId, "Nuevo Nombre", null, null,
                null));

        verify(saveUserPort).save(any());
    }
}
