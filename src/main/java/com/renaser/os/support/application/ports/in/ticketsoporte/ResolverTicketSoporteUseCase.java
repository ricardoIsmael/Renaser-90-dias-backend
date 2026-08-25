package com.renaser.os.support.application.ports.in.ticketsoporte;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.domain.model.ticketsoporte.TicketSoporteId;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public interface ResolverTicketSoporteUseCase {

    /** Devuelve el ticket actualizado — el contrato viejo responde 200 con el ticket completo. */
    TicketSoporteVista resolver(ResolverTicketSoporteCommand command);

    record ResolverTicketSoporteCommand(
            @NotNull TicketSoporteId ticketId,
            @NotNull UserId actorId,
            @Size(max = 4000) String adminNotes) {

        public ResolverTicketSoporteCommand {
            SelfValidating.validateConstructorArgs(ResolverTicketSoporteCommand.class, ticketId, actorId,
                    adminNotes);
        }
    }
}
