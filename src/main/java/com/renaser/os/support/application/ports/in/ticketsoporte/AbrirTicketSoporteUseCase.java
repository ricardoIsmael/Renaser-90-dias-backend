package com.renaser.os.support.application.ports.in.ticketsoporte;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.domain.model.ticketsoporte.CategoriaSoporte;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public interface AbrirTicketSoporteUseCase {

    /** Devuelve el ticket completo con la URL de lectura del adjunto ya firmada (si tiene). */
    TicketSoporteVista abrir(AbrirTicketSoporteCommand command);

    record AbrirTicketSoporteCommand(
            @NotNull UserId usuarioId,
            CategoriaSoporte categoria,
            @NotBlank @Size(max = 200) String asunto,
            @NotBlank @Size(min = 10, max = 4000) String mensaje,
            @Size(max = 4000) String clientLog,
            String adjuntoBucket,
            String adjuntoRuta) {

        public AbrirTicketSoporteCommand {
            SelfValidating.validateConstructorArgs(AbrirTicketSoporteCommand.class, usuarioId, categoria, asunto,
                    mensaje, clientLog, adjuntoBucket, adjuntoRuta);
        }
    }
}
