package com.renaser.os.users.application.ports.in.participante;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import jakarta.validation.constraints.NotNull;

/**
 * Opt-in de "seguimiento personal" (Mis Habitos/Mis Rocas opcional) para staff —
 * MENTOR/MENTOR_LEAD/ADMIN/ALCHEMIST. Replica {@code POST /api/v1/mentor/activate-tracking}
 * del backend viejo (src/app/api/v1/mentor/activate-tracking/route.ts).
 *
 * <p>El comando SOLO lleva {@code actorId} — nunca es sobre otro usuario (self-only por
 * diseño: nadie activa el programa de otro por esta via) y no existe forma de que el
 * cliente mande {@code programDay}/{@code currentPhase}/etc: el servidor los fija
 * siempre (§5.3.3).
 */
public interface ActivateSelfTrackingUseCase {

    ParticipacionPrograma activate(ActivateSelfTrackingCommand command);

    record ActivateSelfTrackingCommand(@NotNull UserId actorId) {

        public ActivateSelfTrackingCommand {
            SelfValidating.validateConstructorArgs(ActivateSelfTrackingCommand.class, actorId);
        }
    }
}
