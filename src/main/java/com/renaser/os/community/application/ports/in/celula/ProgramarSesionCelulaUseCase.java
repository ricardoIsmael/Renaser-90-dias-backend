package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public interface ProgramarSesionCelulaUseCase {

    Celula programar(ProgramarSesionCelulaCommand command);

    record ProgramarSesionCelulaCommand(@NotNull UserId actorId, @NotNull CelulaId celulaId,
                                         @NotNull Instant proximaSesionEn) {

        public ProgramarSesionCelulaCommand {
            SelfValidating.validateConstructorArgs(ProgramarSesionCelulaCommand.class, actorId, celulaId,
                    proximaSesionEn);
        }
    }
}
