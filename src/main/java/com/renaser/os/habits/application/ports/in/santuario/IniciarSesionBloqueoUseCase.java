package com.renaser.os.habits.application.ports.in.santuario;

import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.santuario.SesionBloqueo;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface IniciarSesionBloqueoUseCase {

    SesionBloqueo iniciar(IniciarSesionBloqueoCommand command);

    record IniciarSesionBloqueoCommand(@NotNull UserId actorId, @NotNull RegistroHabitoId registroId) {
        public IniciarSesionBloqueoCommand {
            SelfValidating.validateConstructorArgs(IniciarSesionBloqueoCommand.class, actorId, registroId);
        }
    }
}
