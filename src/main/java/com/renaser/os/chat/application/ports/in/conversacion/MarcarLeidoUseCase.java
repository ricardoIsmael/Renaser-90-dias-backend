package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface MarcarLeidoUseCase {

    void marcarLeido(MarcarLeidoCommand command);

    record MarcarLeidoCommand(@NotNull UserId actorId, @NotNull ConversacionId conversacionId) {

        public MarcarLeidoCommand {
            SelfValidating.validateConstructorArgs(MarcarLeidoCommand.class, actorId, conversacionId);
        }
    }
}
