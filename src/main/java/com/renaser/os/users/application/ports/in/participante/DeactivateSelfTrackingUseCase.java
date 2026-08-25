package com.renaser.os.users.application.ports.in.participante;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * Opt-out del seguimiento personal. Replica {@code DELETE /api/v1/mentor/activate-tracking}
 * del backend viejo: idempotente (una segunda llamada no es un error), self-only.
 */
public interface DeactivateSelfTrackingUseCase {

    /** @return true si habia algo que desactivar; false si ya estaba inactivo (idempotente). */
    boolean deactivate(DeactivateSelfTrackingCommand command);

    record DeactivateSelfTrackingCommand(@NotNull UserId actorId) {

        public DeactivateSelfTrackingCommand {
            SelfValidating.validateConstructorArgs(DeactivateSelfTrackingCommand.class, actorId);
        }
    }
}
