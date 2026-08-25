package com.renaser.os.rocks.application.ports.in.rocadiaria;

import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.net.URI;

/** URL PUT prefirmada (D-34) para subir la evidencia de una Roca Diaria, bucket `rocas/`. */
public interface SolicitarUrlAdjuntoRocaUseCase {

    UrlAdjuntoRoca solicitarUrl(SolicitarUrlAdjuntoRocaCommand command);

    record SolicitarUrlAdjuntoRocaCommand(@NotNull UserId actorId, @NotNull RocaDiariaId rocaDiariaId,
                                           @NotBlank String tipoContenido) {

        public SolicitarUrlAdjuntoRocaCommand {
            SelfValidating.validateConstructorArgs(SolicitarUrlAdjuntoRocaCommand.class, actorId, rocaDiariaId,
                    tipoContenido);
        }
    }

    record UrlAdjuntoRoca(URI url, String bucket, String ruta) {
    }
}
