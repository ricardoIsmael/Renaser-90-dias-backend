package com.renaser.os.habits.application.ports.in.guiaadmin;

import com.renaser.os.habits.domain.model.guia.AdjuntoGuiaId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface EliminarAdjuntoGuiaUseCase {

    void eliminar(EliminarAdjuntoGuiaCommand command);

    record EliminarAdjuntoGuiaCommand(@NotNull UserId actorId, @NotNull AdjuntoGuiaId adjuntoId) {
        public EliminarAdjuntoGuiaCommand {
            SelfValidating.validateConstructorArgs(EliminarAdjuntoGuiaCommand.class, actorId, adjuntoId);
        }
    }
}
