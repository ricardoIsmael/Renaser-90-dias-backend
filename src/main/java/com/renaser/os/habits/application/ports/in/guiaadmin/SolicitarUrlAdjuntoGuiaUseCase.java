package com.renaser.os.habits.application.ports.in.guiaadmin;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.net.URI;

/**
 * Paso 1 del adjunto de guia por archivo (IMAGEN/AUDIO, hueco #11): URL PUT prefirmada,
 * mismo patron "upload-url -> PUT -> confirmar" ya establecido en el resto del sistema
 * (rocks.SolicitarUrlAdjuntoRocaUseCase, users.SolicitarUrlAvatarUseCase). ENLACE sigue
 * sin pasar por aca — ver {@code CrearAdjuntoGuiaEnlaceUseCase}.
 */
public interface SolicitarUrlAdjuntoGuiaUseCase {

    UrlAdjuntoGuia solicitarUrl(SolicitarUrlAdjuntoGuiaCommand command);

    record SolicitarUrlAdjuntoGuiaCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId,
                                           @NotBlank String tipoContenido) {
        public SolicitarUrlAdjuntoGuiaCommand {
            SelfValidating.validateConstructorArgs(SolicitarUrlAdjuntoGuiaCommand.class, actorId, habitoId,
                    tipoContenido);
        }
    }

    record UrlAdjuntoGuia(URI url, String bucket, String ruta) {
    }
}
