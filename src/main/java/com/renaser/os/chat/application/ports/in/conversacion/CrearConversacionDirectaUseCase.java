package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface CrearConversacionDirectaUseCase {

    /** Busca-o-crea, idempotente por {@code claveDirecta} (CLAUDE.MD sec. 5.4.3). */
    Conversacion obtenerOCrear(CrearConversacionDirectaCommand command);

    record CrearConversacionDirectaCommand(@NotNull UserId actorId, @NotNull UserId otroUsuarioId) {

        public CrearConversacionDirectaCommand {
            SelfValidating.validateConstructorArgs(CrearConversacionDirectaCommand.class, actorId, otroUsuarioId);
        }
    }
}
