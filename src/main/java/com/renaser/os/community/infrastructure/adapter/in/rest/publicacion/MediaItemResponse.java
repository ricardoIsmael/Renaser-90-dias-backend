package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarFeedUseCase.MediaFirmada;

/** {@code thumbnailUrl} (spec 049): la miniatura firmada, o {@code null}. La app la usa en la lista y cae a {@code url} si todavía no existe. */
public record MediaItemResponse(String url, String mimeType, String thumbnailUrl) {

    public static MediaItemResponse from(MediaFirmada media) {
        return new MediaItemResponse(media.url().toString(), media.mime(), media.miniatura() == null ? null : media.miniatura().toString());
    }
}
