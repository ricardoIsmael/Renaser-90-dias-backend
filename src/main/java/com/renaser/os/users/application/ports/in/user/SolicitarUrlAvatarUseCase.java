package com.renaser.os.users.application.ports.in.user;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.net.URI;

/**
 * Paso 1 del avatar generico (gap #4): URL PUT prefirmada para que el cliente suba su foto
 * de perfil directo al storage, mismo patron ya establecido en el resto del sistema
 * (rocks.SolicitarUrlAdjuntoRocaUseCase, habits.SolicitarUrlAdjuntoRachaUseCase,
 * calendar EventoService#urlDePortada). Self-only: nadie sube el avatar de otro por esta via.
 */
public interface SolicitarUrlAvatarUseCase {

    UrlAvatar solicitarUrl(SolicitarUrlAvatarCommand command);

    record SolicitarUrlAvatarCommand(@NotNull UserId actorId, @NotBlank String tipoContenido) {
        public SolicitarUrlAvatarCommand {
            SelfValidating.validateConstructorArgs(SolicitarUrlAvatarCommand.class, actorId, tipoContenido);
        }
    }

    record UrlAvatar(URI url, String bucket, String ruta) {
    }
}
