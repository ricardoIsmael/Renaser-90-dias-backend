package com.renaser.os.onboarding.application.ports.in.media;

import com.renaser.os.onboarding.domain.model.media.ClaseMedia;
import com.renaser.os.onboarding.domain.model.media.MediaOnboarding;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Confirma una subida ya hecha a S3 con la URL de {@code ObtenerUrlSubidaMediaUseCase}. */
public interface RegistrarMediaUseCase {

    MediaOnboarding registrar(RegistrarMediaCommand command);

    record RegistrarMediaCommand(@NotNull UserId usuarioId, String flujo, String clavePregunta,
                                  @NotNull ClaseMedia clase, @NotBlank String bucket, @NotBlank String rutaStorage,
                                  String mime, Long tamanoBytes, BigDecimal duracionSegundos, String metadatosJson) {

        public RegistrarMediaCommand {
            SelfValidating.validateConstructorArgs(RegistrarMediaCommand.class, usuarioId, flujo, clavePregunta,
                    clase, bucket, rutaStorage, mime, tamanoBytes, duracionSegundos, metadatosJson);
        }
    }
}
