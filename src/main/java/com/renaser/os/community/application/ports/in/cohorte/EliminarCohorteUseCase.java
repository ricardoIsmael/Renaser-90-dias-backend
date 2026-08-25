package com.renaser.os.community.application.ports.in.cohorte;

import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface EliminarCohorteUseCase {

    void eliminar(EliminarCohorteCommand command);

    record EliminarCohorteCommand(@NotNull UserId actorId, @NotNull CohorteId cohorteId) {

        public EliminarCohorteCommand {
            SelfValidating.validateConstructorArgs(EliminarCohorteCommand.class, actorId, cohorteId);
        }
    }
}
