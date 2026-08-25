package com.renaser.os.community.application.ports.in.cohorte;

import com.renaser.os.community.domain.model.cohorte.Cohorte;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public interface CrearCohorteUseCase {

    Cohorte crear(CrearCohorteCommand command);

    record CrearCohorteCommand(@NotNull UserId actorId, @NotBlank String nombre, @NotNull LocalDate fechaInicio,
                                LocalDate fechaFin) {

        public CrearCohorteCommand {
            SelfValidating.validateConstructorArgs(CrearCohorteCommand.class, actorId, nombre, fechaInicio,
                    fechaFin);
        }
    }
}
