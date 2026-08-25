package com.renaser.os.habits.application.ports.in.registro;

import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface CompletarRegistroUseCase {

    RegistroHabito completar(CompletarRegistroCommand command);

    /** Sin campo `puntos`: el otorgamiento lo calcula el servicio contra la ventana real del habito
     * (mismo blindaje anti mass-assignment que CLAUDE.MD §5.3.3). */
    record CompletarRegistroCommand(@NotNull UserId actorId, @NotNull RegistroHabitoId registroId,
                                     String respuestaTexto, Integer calificacionProductividad) {

        public CompletarRegistroCommand {
            SelfValidating.validateConstructorArgs(CompletarRegistroCommand.class, actorId, registroId,
                    respuestaTexto, calificacionProductividad);
        }
    }
}
