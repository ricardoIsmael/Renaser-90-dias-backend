package com.renaser.os.support.application.ports.in.ticketsoporte;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.net.URI;

public interface SolicitarUrlAdjuntoSoporteUseCase {

    UrlAdjuntoSoporte solicitar(SolicitarUrlAdjuntoCommand command);

    record SolicitarUrlAdjuntoCommand(
            @NotNull UserId usuarioId,
            @NotBlank String nombreArchivo,
            @NotBlank String tipoContenido) {

        public SolicitarUrlAdjuntoCommand {
            SelfValidating.validateConstructorArgs(SolicitarUrlAdjuntoCommand.class, usuarioId, nombreArchivo,
                    tipoContenido);
        }
    }

    record UrlAdjuntoSoporte(String bucket, String ruta, URI urlSubida) {
    }
}
