package com.renaser.os.users.application.ports.in.admin;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.domain.model.user.User;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Panel admin de staff (gap #6 de docs/PLAN_INTEGRACION_FRONTEND.md): mentores, lider de
 * mentores, admin y alquimista — nunca TRAINEE, ese es el panel de aprendices (#7).
 */
public interface ListStaffUseCase {

    PaginaStaff listar(ListStaffCommand command);

    /** El staff son los 4 roles distintos de TRAINEE — conjunto cerrado, no una regla de negocio inventada. */
    Set<UserRole> ROLES_STAFF = EnumSet.of(UserRole.MENTOR, UserRole.MENTOR_LEAD, UserRole.ADMIN, UserRole.ALCHEMIST);

    record ListStaffCommand(UserId actorId, UserRole roleFilter, UserStatus statusFilter, int page, int size) {

        public ListStaffCommand {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            if (roleFilter != null && !ROLES_STAFF.contains(roleFilter)) {
                throw new IllegalArgumentException("roleFilter debe ser un rol de staff: " + roleFilter);
            }
            if (page < 0) {
                throw new IllegalArgumentException("page no puede ser negativo");
            }
            if (size <= 0 || size > 200) {
                throw new IllegalArgumentException("size debe estar entre 1 y 200");
            }
        }
    }

    record PaginaStaff(List<User> contenido, long total, int page, int size) {
    }
}
