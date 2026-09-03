package com.renaser.os.community.application.ports.in.cohorte;

import com.renaser.os.community.application.ports.in.cohorte.ConsultarCohortesUseCase.CohorteResumen;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.community.domain.model.cohorte.EstadoCohorte;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface CambiarEstadoCohorteUseCase {

    /** Proyeccion de respuesta dentro de la misma transaccion (CLAUDE.MD sec. 5.4.6). */
    CohorteResumen cambiarEstado(CambiarEstadoCohorteCommand command);

    record CambiarEstadoCohorteCommand(@NotNull UserId actorId, @NotNull CohorteId cohorteId,
                                        @NotNull EstadoCohorte nuevoEstado) {

        public CambiarEstadoCohorteCommand {
            SelfValidating.validateConstructorArgs(CambiarEstadoCohorteCommand.class, actorId, cohorteId,
                    nuevoEstado);
        }
    }
}
