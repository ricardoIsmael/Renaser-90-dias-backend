package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarFeedUseCase.MediaFirmada;

public record MediaItemResponse(String url, String mimeType) {

    public static MediaItemResponse from(MediaFirmada media) {
        return new MediaItemResponse(media.url().toString(), media.mime());
    }
}
