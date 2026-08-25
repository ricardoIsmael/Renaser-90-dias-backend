package com.renaser.os.community.application.ports.in.publicacion;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.net.URI;

/**
 * Vuelta nueva: URL prefirmada de subida via {@code AlmacenamientoPort} (CLAUDE.MD sec.
 * "STORAGE"), mismo patron que `rocks` (SolicitarUrlAdjuntoRocaUseCase) y `phasecontracts`
 * (ObtenerUrlFirmaContratoUseCase). Hoy el adapter es NoOp (faltan credenciales AWS, D-34) —
 * el puerto se usa igual para que el modulo quede listo apenas se resuelva.
 */
public interface SolicitarUrlSubidaMediaUseCase {

    UrlSubidaMedia solicitarUrl(SolicitarUrlSubidaMediaCommand command);

    record SolicitarUrlSubidaMediaCommand(@NotNull UserId actorId, @NotBlank String tipoContenido) {

        public SolicitarUrlSubidaMediaCommand {
            SelfValidating.validateConstructorArgs(SolicitarUrlSubidaMediaCommand.class, actorId, tipoContenido);
        }
    }

    record UrlSubidaMedia(URI url, String bucket, String ruta) {
    }
}
