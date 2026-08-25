package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocadiaria;

import com.renaser.os.rocks.application.ports.in.rocadiaria.SolicitarUrlAdjuntoRocaUseCase.UrlAdjuntoRoca;

public record UrlAdjuntoResponse(String uploadUrl, String bucket, String ruta) {

    public static UrlAdjuntoResponse from(UrlAdjuntoRoca url) {
        return new UrlAdjuntoResponse(url.url().toString(), url.bucket(), url.ruta());
    }
}
