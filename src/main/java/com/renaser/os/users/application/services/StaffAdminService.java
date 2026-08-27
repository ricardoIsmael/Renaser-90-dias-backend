package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.admin.ListStaffUseCase;
import com.renaser.os.users.application.ports.in.admin.UpdateStaffProfileUseCase;
import com.renaser.os.users.application.ports.in.admin.UpdateUserStatusUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.CerrarTodasLasSesionesUseCase;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Panel admin de staff (gap #6 de docs/PLAN_INTEGRACION_FRONTEND.md): listar, cambiar
 * estado y editar a otro usuario. Orden de los guards, DESPUES de confirmar que el
 * recurso objetivo existe (docs/BITACORA_ERRORES.md E-42): primero se carga el target,
 * despues se verifica el actor con {@link RequireAdminGuard} (fail-closed, nunca lanza
 * para un actor inexistente — asi un actor invalido siempre cae a 403, nunca a un 404
 * con mensaje distinto que delataria si el target existia).
 */
@Service
class StaffAdminService implements ListStaffUseCase, UpdateUserStatusUseCase, UpdateStaffProfileUseCase {

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final RequireAdminGuard requireAdminGuard;
    private final CerrarTodasLasSesionesUseCase cerrarTodasLasSesionesUseCase;

    StaffAdminService(LoadUserPort loadUserPort, SaveUserPort saveUserPort, RequireAdminGuard requireAdminGuard,
                       CerrarTodasLasSesionesUseCase cerrarTodasLasSesionesUseCase) {
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.requireAdminGuard = requireAdminGuard;
        this.cerrarTodasLasSesionesUseCase = cerrarTodasLasSesionesUseCase;
    }

    /** Listado sin un recurso previo por id: el gate de admin va primero, no hay orden que respetar. */
    @Override
    public PaginaStaff listar(ListStaffCommand command) {
        requireAdminGuard.requireAdminActivo(command.actorId());
        var roles = command.roleFilter() != null ? java.util.Set.of(command.roleFilter()) : ROLES_STAFF;
        var contenido = loadUserPort.byRoles(roles, command.statusFilter(), command.page(), command.size());
        long total = loadUserPort.countByRoles(roles, command.statusFilter());
        return new PaginaStaff(contenido, total, command.page(), command.size());
    }

    @Override
    @Transactional
    public void updateStatus(UpdateUserStatusCommand command) {
        User target = requireTarget(command.targetUserId());
        requireAdminGuard.requireAdminActivo(command.actorId());

        applyStatus(target, command.newStatus());
        saveUserPort.save(target);

        // docs/MODULO_AUTH.md §7.4: suspender revoca TODAS las sesiones en el acto, no solo
        // corta accesos futuros.
        if (command.newStatus() == UserStatus.SUSPENDED) {
            cerrarTodasLasSesionesUseCase.cerrarTodas(target.id());
        }
    }

    @Override
    @Transactional
    public void updateStaffProfile(UpdateStaffProfileCommand command) {
        User target = requireTarget(command.targetUserId());
        requireAdminGuard.requireAdminActivo(command.actorId());

        if (command.fullName() != null) {
            target.rename(command.fullName());
        }
        if (command.avatarUrl() != null) {
            target.changeAvatar(command.avatarUrl());
        }
        if (command.bio() != null) {
            target.updateBio(command.bio());
        }
        if (command.department() != null) {
            target.updateDepartment(command.department());
        }
        saveUserPort.save(target);
    }

    private static void applyStatus(User target, UserStatus newStatus) {
        switch (newStatus) {
            case ACTIVE -> target.reactivate();
            case SUSPENDED -> target.suspend();
            // INACTIVE es "registrado, sin aprobar todavia" (R-3, 2026-08-27): lo pone el
            // autoregistro y lo saca la aprobacion. Un admin no puede devolver a alguien de
            // staff a ese estado desde este panel — no seria "desactivar", seria dejarlo
            // esperando una aprobacion que ya ocurrio. Para quitarle el acceso esta SUSPENDED.
            case INACTIVE -> throw new IllegalArgumentException(
                    "No se puede pasar una cuenta a pendiente de aprobacion; para quitar acceso, suspendela");
        }
    }

    private User requireTarget(UserId targetUserId) {
        return loadUserPort.byId(targetUserId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + targetUserId));
    }
}
