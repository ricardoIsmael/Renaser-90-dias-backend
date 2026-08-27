package com.renaser.os.users.application.ports.in.participante;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * Quita a un participante de su celula actual (gap #25). Contraparte de
 * {@link AssignTraineeCellUseCase} — idempotente: quitar a alguien que ya no tiene celula
 * no falla (mismo criterio que {@code DeactivateSelfTrackingUseCase}).
 */
public interface RemoveTraineeCellUseCase {

    void remove(RemoveTraineeCellCommand command);

    record RemoveTraineeCellCommand(@NotNull UserId actorId, @NotNull UserId traineeId) {

        public RemoveTraineeCellCommand {
            SelfValidating.validateConstructorArgs(RemoveTraineeCellCommand.class, actorId, traineeId);
        }
    }
}
