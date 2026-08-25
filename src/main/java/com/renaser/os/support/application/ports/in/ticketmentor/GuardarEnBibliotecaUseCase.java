package com.renaser.os.support.application.ports.in.ticketmentor;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentorId;
import jakarta.validation.constraints.NotNull;

public interface GuardarEnBibliotecaUseCase {

    /** Devuelve el ticket actualizado — el contrato viejo responde 200 con el ticket completo. */
    TicketMentor guardar(GuardarEnBibliotecaCommand command);

    record GuardarEnBibliotecaCommand(@NotNull TicketMentorId ticketId, @NotNull UserId actorId) {

        public GuardarEnBibliotecaCommand {
            SelfValidating.validateConstructorArgs(GuardarEnBibliotecaCommand.class, ticketId, actorId);
        }
    }
}
