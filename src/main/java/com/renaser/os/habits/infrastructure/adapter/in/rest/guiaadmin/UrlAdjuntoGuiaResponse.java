package com.renaser.os.habits.infrastructure.adapter.in.rest.guiaadmin;

import com.renaser.os.habits.application.ports.in.guiaadmin.SolicitarUrlAdjuntoGuiaUseCase.UrlAdjuntoGuia;

public record UrlAdjuntoGuiaResponse(String uploadUrl, String bucket, String ruta) {

    public static UrlAdjuntoGuiaResponse from(UrlAdjuntoGuia url) {
        return new UrlAdjuntoGuiaResponse(url.url().toString(), url.bucket(), url.ruta());
    }
}
