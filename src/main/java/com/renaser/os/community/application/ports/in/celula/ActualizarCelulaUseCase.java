package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.CelulaDetalle;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface ActualizarCelulaUseCase {

    /** Proyeccion de respuesta dentro de la misma transaccion (CLAUDE.MD sec. 5.4.6). */
    CelulaDetalle actualizar(ActualizarCelulaCommand command);

    /** {@code tocaUrlVideollamada} distingue "no vino" de "vino null para borrarla"
     * (community/schema.ts:48-51). */
    record ActualizarCelulaCommand(@NotNull UserId actorId, @NotNull CelulaId celulaId, String nombre,
                                    String urlVideollamada, boolean tocaUrlVideollamada) {

        public ActualizarCelulaCommand {
            SelfValidating.validateConstructorArgs(ActualizarCelulaCommand.class, actorId, celulaId, nombre,
                    urlVideollamada, tocaUrlVideollamada);
        }
    }
}
