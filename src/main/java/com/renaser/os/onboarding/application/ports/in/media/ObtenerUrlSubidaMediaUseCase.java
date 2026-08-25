package com.renaser.os.onboarding.application.ports.in.media;

import com.renaser.os.onboarding.domain.model.media.ClaseMedia;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.net.URI;

public interface ObtenerUrlSubidaMediaUseCase {

    UrlSubidaMedia obtener(ObtenerUrlSubidaMediaCommand command);

    record ObtenerUrlSubidaMediaCommand(@NotNull UserId usuarioId, String flujo, String clavePregunta,
                                         @NotNull ClaseMedia clase, @NotBlank String tipoContenido) {

        public ObtenerUrlSubidaMediaCommand {
            SelfValidating.validateConstructorArgs(ObtenerUrlSubidaMediaCommand.class, usuarioId, flujo,
                    clavePregunta, clase, tipoContenido);
        }
    }

    record UrlSubidaMedia(URI urlSubida, String bucket, String ruta) {
    }
}
