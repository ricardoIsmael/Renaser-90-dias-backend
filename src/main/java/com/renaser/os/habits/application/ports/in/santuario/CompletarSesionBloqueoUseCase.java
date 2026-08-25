package com.renaser.os.habits.application.ports.in.santuario;

import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.santuario.SesionBloqueo;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface CompletarSesionBloqueoUseCase {

    SesionBloqueo completar(CompletarSesionBloqueoCommand command);

    record CompletarSesionBloqueoCommand(@NotNull UserId actorId, @NotNull RegistroHabitoId registroId) {
        public CompletarSesionBloqueoCommand {
            SelfValidating.validateConstructorArgs(CompletarSesionBloqueoCommand.class, actorId, registroId);
        }
    }
}
