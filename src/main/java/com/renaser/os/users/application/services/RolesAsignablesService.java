package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.in.admin.ConsultarRolesAsignablesUseCase;
import com.renaser.os.users.application.ports.out.rolpersonalizado.CargarRolesPersonalizadosPort;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code GET /admin/roles-asignables} (D-272): primero los seis roles base, en el orden en que la app los ofrece desde
 * D-265 (Aprendiz primero), y después los personalizados activos, por nombre. Uno que hereda de ADMIN o ALQUIMISTA
 * figura también para un ALQUIMISTA, como hoy los roles base: quien lo rechaza es la asignación (403).
 */
@Service
class RolesAsignablesService implements ConsultarRolesAsignablesUseCase {

    /** Los seis roles base que Administración asigna, en el orden de la app (D-265). Soporte TI no (D-267). */
    static final List<UserRole> ROLES_BASE = List.of(UserRole.TRAINEE, UserRole.TRAINEE_GUIDE, UserRole.MENTOR,
            UserRole.MENTOR_LEAD, UserRole.ADMIN, UserRole.ALCHEMIST);

    private final RequireAdminGuard requireAdminGuard;
    private final CargarRolesPersonalizadosPort cargar;

    RolesAsignablesService(RequireAdminGuard requireAdminGuard, CargarRolesPersonalizadosPort cargar) {
        this.requireAdminGuard = requireAdminGuard;
        this.cargar = cargar;
    }

    @Override
    public List<RolAsignable> rolesAsignables(UserId actor) {
        requireAdminGuard.requireAdminActivo(actor);
        List<RolAsignable> roles = new ArrayList<>();
        ROLES_BASE.forEach(rol -> roles.add(new RolAsignable(rol.name(), rol.nombreVisible(), rol, null)));
        cargar.todos().stream().filter(RolPersonalizado::activo)
                .forEach(rol -> roles.add(new RolAsignable(rol.clave(), rol.nombre(), rol.rolBase(), rol.id())));
        return List.copyOf(roles);
    }
}
