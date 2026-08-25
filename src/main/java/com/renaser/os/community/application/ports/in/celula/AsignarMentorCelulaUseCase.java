package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface AsignarMentorCelulaUseCase {

    Celula asignar(AsignarMentorCelulaCommand command);

    record AsignarMentorCelulaCommand(@NotNull UserId actorId, @NotNull CelulaId celulaId,
                                       @NotNull UserId mentorId) {

        public AsignarMentorCelulaCommand {
            SelfValidating.validateConstructorArgs(AsignarMentorCelulaCommand.class, actorId, celulaId, mentorId);
        }
    }
}
