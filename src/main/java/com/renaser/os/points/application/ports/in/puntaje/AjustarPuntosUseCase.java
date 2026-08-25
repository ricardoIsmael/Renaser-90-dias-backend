package com.renaser.os.points.application.ports.in.puntaje;

import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.points.domain.model.ajuste.AjustePuntos;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface AjustarPuntosUseCase {

    AjustePuntos ajustar(AjustarPuntosCommand command);

    record AjustarPuntosCommand(@NotNull UserId participanteId, @NotNull MotivoPuntos motivo, int delta,
                                 String nota) {

        public AjustarPuntosCommand {
            SelfValidating.validateConstructorArgs(AjustarPuntosCommand.class, participanteId, motivo, delta, nota);
        }
    }
}
