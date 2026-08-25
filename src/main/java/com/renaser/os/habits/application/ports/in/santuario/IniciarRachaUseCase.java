package com.renaser.os.habits.application.ports.in.santuario;

import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.santuario.RachaSinCelular;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface IniciarRachaUseCase {

    RachaSinCelular iniciar(IniciarRachaCommand command);

    record IniciarRachaCommand(@NotNull UserId actorId, @NotNull RegistroHabitoId registroId, int horasObjetivo) {
        public IniciarRachaCommand {
            SelfValidating.validateConstructorArgs(IniciarRachaCommand.class, actorId, registroId, horasObjetivo);
        }
    }
}
