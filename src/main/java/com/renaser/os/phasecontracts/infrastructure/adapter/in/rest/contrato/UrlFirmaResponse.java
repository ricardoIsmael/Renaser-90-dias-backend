package com.renaser.os.phasecontracts.infrastructure.adapter.in.rest.contrato;

import com.renaser.os.phasecontracts.application.ports.in.contrato.ObtenerUrlFirmaContratoUseCase.UrlFirmaContrato;

public record UrlFirmaResponse(String uploadUrl, String bucket, String path) {

    static UrlFirmaResponse from(UrlFirmaContrato url) {
        return new UrlFirmaResponse(url.urlSubida().toString(), url.bucket(), url.ruta());
    }
}
