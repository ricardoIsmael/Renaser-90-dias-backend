package com.renaser.os.habits.application.ports.in.guiaadmin;

import com.renaser.os.habits.domain.model.guia.GuiaHabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/** Borra una guia (y en cascada sus adjuntos, `ON DELETE CASCADE`). Solo ADMIN/ALCHEMIST. */
public interface EliminarGuiaHabitoUseCase {

    void eliminar(EliminarGuiaHabitoCommand command);

    record EliminarGuiaHabitoCommand(@NotNull UserId actorId, @NotNull GuiaHabitoId guiaId) {
        public EliminarGuiaHabitoCommand {
            SelfValidating.validateConstructorArgs(EliminarGuiaHabitoCommand.class, actorId, guiaId);
        }
    }
}
