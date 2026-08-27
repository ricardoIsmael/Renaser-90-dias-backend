package com.renaser.os.habits.application.ports.in.horarioadmin;

import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/** Borrado de un horario de catalogo. Solo ADMIN/ALCHEMIST. */
public interface EliminarHorarioHabitoUseCase {

    void eliminar(EliminarHorarioHabitoCommand command);

    record EliminarHorarioHabitoCommand(@NotNull UserId actorId, @NotNull HorarioHabitoId horarioId) {
        public EliminarHorarioHabitoCommand {
            SelfValidating.validateConstructorArgs(EliminarHorarioHabitoCommand.class, actorId, horarioId);
        }
    }
}
