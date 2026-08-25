package com.renaser.os.support.infrastructure.adapter.in.rest.ticketsoporte;

import com.renaser.os.support.application.ports.in.ticketsoporte.SolicitarUrlAdjuntoSoporteUseCase.UrlAdjuntoSoporte;

public record UrlAdjuntoResponse(String bucket, String path, String uploadUrl) {

    public static UrlAdjuntoResponse from(UrlAdjuntoSoporte urlAdjunto) {
        return new UrlAdjuntoResponse(urlAdjunto.bucket(), urlAdjunto.ruta(), urlAdjunto.urlSubida().toString());
    }
}
