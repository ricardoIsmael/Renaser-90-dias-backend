package com.renaser.os.support.application.ports.in.ticketmentor;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.TicketsAlMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

public interface AbrirTicketMentorUseCase {

    TicketMentor abrir(AbrirTicketMentorCommand command);

    record AbrirTicketMentorCommand(
            @NotNull UserId participanteId,
            @NotBlank @Size(max = TicketsAlMentor.LARGO_MAXIMO_TEXTO) String descripcionBloqueo,
            @NotBlank @Size(max = TicketsAlMentor.LARGO_MAXIMO_TEXTO) String solucionesIntentadas,
            @NotBlank @Size(max = TicketsAlMentor.LARGO_MAXIMO_TEXTO) String impactoMetaSmart) {

        public AbrirTicketMentorCommand {
            SelfValidating.validateConstructorArgs(AbrirTicketMentorCommand.class, participanteId,
                    descripcionBloqueo, solucionesIntentadas, impactoMetaSmart);
        }
    }
}
