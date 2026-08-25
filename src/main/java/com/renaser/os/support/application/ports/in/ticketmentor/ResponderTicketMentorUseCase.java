package com.renaser.os.support.application.ports.in.ticketmentor;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentorId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

public interface ResponderTicketMentorUseCase {

    /** Devuelve el ticket actualizado — el contrato viejo responde 200 con el ticket completo. */
    TicketMentor responder(ResponderTicketMentorCommand command);

    record ResponderTicketMentorCommand(
            @NotNull TicketMentorId ticketId,
            @NotNull UserId actorId,
            @NotBlank @Size(max = 4000) String respuesta) {

        public ResponderTicketMentorCommand {
            SelfValidating.validateConstructorArgs(ResponderTicketMentorCommand.class, ticketId, actorId, respuesta);
        }
    }
}
