package com.renaser.os.points.application.ports.in.puntaje;

import com.renaser.os.points.domain.model.ajuste.AjustePuntos;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface AjustarPuntosManualmenteUseCase {

    AjustePuntos ajustarManualmente(AjustarPuntosManualmenteCommand command);

    record AjustarPuntosManualmenteCommand(@NotNull UserId participanteId, int delta, String nota,
                                            @NotNull UserId actorId) {

        public AjustarPuntosManualmenteCommand {
            SelfValidating.validateConstructorArgs(AjustarPuntosManualmenteCommand.class, participanteId, delta,
                    nota, actorId);
        }
    }
}
