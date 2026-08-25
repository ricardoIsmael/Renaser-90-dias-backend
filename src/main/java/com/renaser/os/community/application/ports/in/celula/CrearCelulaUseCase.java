package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public interface CrearCelulaUseCase {

    Celula crear(CrearCelulaCommand command);

    record CrearCelulaCommand(@NotNull UserId actorId, @NotBlank String nombre, @NotNull CohorteId cohorteId,
                               String urlVideollamada) {

        public CrearCelulaCommand {
            SelfValidating.validateConstructorArgs(CrearCelulaCommand.class, actorId, nombre, cohorteId,
                    urlVideollamada);
        }
    }
}
