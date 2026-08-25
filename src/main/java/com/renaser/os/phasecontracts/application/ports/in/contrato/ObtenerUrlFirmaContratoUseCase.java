package com.renaser.os.phasecontracts.application.ports.in.contrato;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.net.URI;

public interface ObtenerUrlFirmaContratoUseCase {

    UrlFirmaContrato obtenerUrlSubida(ObtenerUrlFirmaContratoCommand command);

    record ObtenerUrlFirmaContratoCommand(@NotNull UserId participanteId) {

        public ObtenerUrlFirmaContratoCommand {
            SelfValidating.validateConstructorArgs(ObtenerUrlFirmaContratoCommand.class, participanteId);
        }
    }

    record UrlFirmaContrato(URI urlSubida, String bucket, String ruta) {
    }
}
