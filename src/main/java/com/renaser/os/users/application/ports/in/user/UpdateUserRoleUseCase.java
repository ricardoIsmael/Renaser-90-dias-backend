package com.renaser.os.users.application.ports.in.user;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import jakarta.validation.constraints.NotNull;

/**
 * Solo el camino simple (CLAUDE.MD §4.3): si el nuevo rol es MENTOR y no existe
 * MentorProfile todavia, se crea uno vacio. Migracion de datos entre perfiles al
 * cambiar de rol queda fuera de alcance, explicitamente.
 */
public interface UpdateUserRoleUseCase {

    void updateRole(UpdateUserRoleCommand command);

    record UpdateUserRoleCommand(
            @NotNull UserId targetUserId,
            @NotNull UserRole newRole,
            @NotNull UserId actorId) {

        public UpdateUserRoleCommand {
            SelfValidating.validateConstructorArgs(UpdateUserRoleCommand.class, targetUserId, newRole, actorId);
        }
    }
}
