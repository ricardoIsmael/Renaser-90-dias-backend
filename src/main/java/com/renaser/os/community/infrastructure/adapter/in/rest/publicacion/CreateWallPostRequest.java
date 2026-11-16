package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.PublicarUseCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code formation} (spec 024): solo la manda Administración, que no pertenece a una formación y tiene que elegir en el Muro de cuál publica. Para
 * todos los demás se ignora: publican en la suya (y una que no es la suya se rechaza). Ausente = la de quien publica (la app 1.6.0 no la manda).
 */
public record CreateWallPostRequest(@NotBlank @Size(max = 5000) String text,
                                     @NotEmpty @Size(max = 10) List<MediaItemRequest> media, String category,
                                     String formation, java.time.Instant hechoEn, java.util.UUID idCliente,
                                     String origenDeLaHora) {
    public CreateWallPostRequest(String text, List<MediaItemRequest> media, String category) {
        this(text, media, category, null);
    }

    public CreateWallPostRequest(String text, List<MediaItemRequest> media, String category, String formation) {
        this(text, media, category, formation, null, null, null);
    }

    /** Spec 048, fase 2 (aditivo): la publicacion hecha sin internet trae su hora real y el id del envio. */
    PublicarUseCase.HechoSinRed sinRed() {
        return hechoEn != null && idCliente != null ? new PublicarUseCase.HechoSinRed(hechoEn, idCliente, origenDeLaHora) : null;
    }
}
