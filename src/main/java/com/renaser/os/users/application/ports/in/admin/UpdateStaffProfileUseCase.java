package com.renaser.os.users.application.ports.in.admin;

import com.renaser.os.shared.domain.UserId;

import java.util.Objects;

/**
 * "Editar a otro usuario" del panel admin de staff (gap #6) — mismos campos editables que
 * {@code UpdateMyProfileUseCase} (fullName/avatarUrl/bio/department), pero con un
 * {@code targetUserId} distinto del actor y gate ADMIN/ALCHEMIST en vez de "uno mismo".
 * Deliberadamente SIN {@code role}: cambiar el rol sigue siendo {@code UpdateUserRoleUseCase}
 * (ya existe, endpoint propio) — no se duplica esa regla de negocio aca.
 */
public interface UpdateStaffProfileUseCase {

    void updateStaffProfile(UpdateStaffProfileCommand command);

    record UpdateStaffProfileCommand(UserId actorId, UserId targetUserId, String fullName, String avatarUrl,
                                      String bio, String department) {

        public UpdateStaffProfileCommand {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(targetUserId, "targetUserId es obligatorio");
        }
    }
}
