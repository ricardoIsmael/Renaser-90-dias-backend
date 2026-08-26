package com.renaser.os.habits.application.ports.in.renombre;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface QuitarRenombreHabitoUseCase {

    /** Vuelve al nombre del catalogo. Misma ventana que para ponerlo (hasta el dia 0). */
    void quitar(QuitarRenombreHabitoCommand command);

    record QuitarRenombreHabitoCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId) {
        public QuitarRenombreHabitoCommand {
            SelfValidating.validateConstructorArgs(QuitarRenombreHabitoCommand.class, actorId, habitoId);
        }
    }
}
