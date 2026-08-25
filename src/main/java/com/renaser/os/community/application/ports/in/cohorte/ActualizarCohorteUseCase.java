package com.renaser.os.community.application.ports.in.cohorte;

import com.renaser.os.community.domain.model.cohorte.Cohorte;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public interface ActualizarCohorteUseCase {

    Cohorte actualizar(ActualizarCohorteCommand command);

    /** {@code nombre}/{@code fechaInicio} null = no se tocan. {@code tocaFechaFin} distingue
     * "no vino en el body" de "vino explicitamente null para borrarla" (community/service.ts:165). */
    record ActualizarCohorteCommand(@NotNull UserId actorId, @NotNull CohorteId cohorteId, String nombre,
                                     LocalDate fechaInicio, LocalDate fechaFin, boolean tocaFechaFin) {

        public ActualizarCohorteCommand {
            SelfValidating.validateConstructorArgs(ActualizarCohorteCommand.class, actorId, cohorteId, nombre,
                    fechaInicio, fechaFin, tocaFechaFin);
        }
    }
}
