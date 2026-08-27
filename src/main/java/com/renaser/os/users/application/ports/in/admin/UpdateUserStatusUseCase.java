package com.renaser.os.users.application.ports.in.admin;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;

import java.util.Objects;

/**
 * Suspender/reactivar staff (gap #6). D-49/docs/MODULO_AUTH.md §7.4: suspender revoca
 * TODAS las sesiones del usuario en el acto (no solo corta accesos futuros) — la
 * implementacion invoca {@code CerrarTodasLasSesionesUseCase} al pasar a SUSPENDED.
 */
public interface UpdateUserStatusUseCase {

    void updateStatus(UpdateUserStatusCommand command);

    record UpdateUserStatusCommand(UserId actorId, UserId targetUserId, UserStatus newStatus) {

        public UpdateUserStatusCommand {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(targetUserId, "targetUserId es obligatorio");
            Objects.requireNonNull(newStatus, "newStatus es obligatorio");
        }
    }
}
