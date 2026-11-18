package com.renaser.os.support.application.ports.in.ticketmentor;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentorId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

public interface ResponderTicketMentorUseCase {

    /**
     * Devuelve el ticket actualizado con el nombre de quien lo respondió — el contrato viejo responde 200 con el ticket completo.
     *
     * <p>Responde el mentor asignado al aprendiz o, desde el 18/11 (spec 056, FR-030), el líder de mentores de la formación del aprendiz.
     *
     * @throws com.renaser.os.shared.domain.NotAuthorizedException 403 si no es el mentor asignado ni el líder de la formación del aprendiz; 403
     *         {@code FUERA_DE_SU_ALCANCE} si es un líder y el aprendiz es de una formación que no tiene
     */
    TicketConAutor responder(ResponderTicketMentorCommand command);

    record ResponderTicketMentorCommand(
            @NotNull TicketMentorId ticketId,
            @NotNull UserId actorId,
            @NotBlank @Size(max = 4000) String respuesta) {

        public ResponderTicketMentorCommand {
            SelfValidating.validateConstructorArgs(ResponderTicketMentorCommand.class, ticketId, actorId, respuesta);
        }
    }
}
