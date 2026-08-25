package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.SolicitarUrlSubidaMediaUseCase.UrlSubidaMedia;

public record UrlSubidaMediaResponse(String uploadUrl, String bucket, String ruta) {

    public static UrlSubidaMediaResponse from(UrlSubidaMedia url) {
        return new UrlSubidaMediaResponse(url.url().toString(), url.bucket(), url.ruta());
    }
}
