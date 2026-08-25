package com.renaser.os.habits.application.ports.in.santuario;

import com.renaser.os.habits.domain.model.santuario.RachaSinCelular;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface RomperRachaUseCase {

    /** Sin penalizacion de puntos (honor-based, phoneFree.ts) — el track vuelve a PENDIENTE o EXPIRADO. */
    RachaSinCelular romper(RomperRachaCommand command);

    record RomperRachaCommand(@NotNull UserId actorId, String motivo) {
        public RomperRachaCommand {
            SelfValidating.validateConstructorArgs(RomperRachaCommand.class, actorId, motivo);
        }
    }
}
