package com.renaser.os.onboarding.infrastructure.adapter.in.rest.media;

import com.renaser.os.onboarding.application.ports.in.media.ObtenerUrlSubidaMediaUseCase.UrlSubidaMedia;

public record UrlSubidaMediaResponse(String uploadUrl, String bucket, String path) {

    public static UrlSubidaMediaResponse from(UrlSubidaMedia url) {
        return new UrlSubidaMediaResponse(url.urlSubida().toString(), url.bucket(), url.ruta());
    }
}
