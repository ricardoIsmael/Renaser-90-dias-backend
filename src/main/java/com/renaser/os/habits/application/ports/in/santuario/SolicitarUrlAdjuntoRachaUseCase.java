package com.renaser.os.habits.application.ports.in.santuario;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.net.URI;

/**
 * URL PUT prefirmada (D-34) para la evidencia con la que se cierra la racha "Dia sin
 * celular" — bucket propio `dia-sin-celular/`, mismo patron que
 * {@code rocks.SolicitarUrlAdjuntoRocaUseCase}.
 */
public interface SolicitarUrlAdjuntoRachaUseCase {

    UrlAdjuntoRacha solicitarUrl(SolicitarUrlAdjuntoRachaCommand command);

    record SolicitarUrlAdjuntoRachaCommand(@NotNull UserId actorId, @NotBlank String tipoContenido) {
        public SolicitarUrlAdjuntoRachaCommand {
            SelfValidating.validateConstructorArgs(SolicitarUrlAdjuntoRachaCommand.class, actorId, tipoContenido);
        }
    }

    record UrlAdjuntoRacha(URI url, String bucket, String ruta) {
    }
}
