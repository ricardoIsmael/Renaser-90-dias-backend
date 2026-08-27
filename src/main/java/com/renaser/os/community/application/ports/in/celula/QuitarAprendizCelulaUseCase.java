package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/** Contraparte de {@link AsignarAprendizCelulaUseCase} — gap #25. */
public interface QuitarAprendizCelulaUseCase {

    void quitar(QuitarAprendizCelulaCommand command);

    record QuitarAprendizCelulaCommand(@NotNull UserId actorId, @NotNull UserId traineeId) {

        public QuitarAprendizCelulaCommand {
            SelfValidating.validateConstructorArgs(QuitarAprendizCelulaCommand.class, actorId, traineeId);
        }
    }
}
