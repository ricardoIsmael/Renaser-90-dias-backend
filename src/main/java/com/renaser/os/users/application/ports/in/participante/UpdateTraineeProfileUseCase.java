package com.renaser.os.users.application.ports.in.participante;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import jakarta.validation.constraints.NotNull;

/**
 * U-05 (CLAUDE.MD §5.3.3): edicion self-only del propio perfil de participante. El comando
 * NO tiene {@code programDay}/{@code coherenceScore}/{@code leaguePoints}/{@code currentPhase}
 * ni {@code goalType} — el compilador los excluye, igual que {@code UpdateMyProfileCommand}
 * en `users/api`. Hoy solo expone {@code personalChallengeName} porque es el unico campo
 * que el frontend real (services/profile.ts#UpdateTraineeProfileInput) manda a
 * {@code PATCH /api/v1/users/me/trainee-profile}.
 */
public interface UpdateTraineeProfileUseCase {

    ParticipacionPrograma updateMyTraineeProfile(UpdateTraineeProfileCommand command);

    /** {@code personalChallengeName == null} = "no cambiar" (semantica de PATCH parcial). */
    record UpdateTraineeProfileCommand(@NotNull UserId actorId, String personalChallengeName) {

        public UpdateTraineeProfileCommand {
            SelfValidating.validateConstructorArgs(UpdateTraineeProfileCommand.class, actorId, personalChallengeName);
        }
    }
}
