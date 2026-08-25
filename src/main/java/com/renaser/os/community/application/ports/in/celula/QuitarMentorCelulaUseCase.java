package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface QuitarMentorCelulaUseCase {

    Celula quitar(QuitarMentorCelulaCommand command);

    record QuitarMentorCelulaCommand(@NotNull UserId actorId, @NotNull CelulaId celulaId) {

        public QuitarMentorCelulaCommand {
            SelfValidating.validateConstructorArgs(QuitarMentorCelulaCommand.class, actorId, celulaId);
        }
    }
}
