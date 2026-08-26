package com.renaser.os.habits.infrastructure.adapter.in.rest.racha;

import com.renaser.os.habits.application.ports.in.santuario.SolicitarUrlAdjuntoRachaUseCase.UrlAdjuntoRacha;

public record UrlAdjuntoRachaResponse(String uploadUrl, String bucket, String ruta) {

    public static UrlAdjuntoRachaResponse from(UrlAdjuntoRacha url) {
        return new UrlAdjuntoRachaResponse(url.url().toString(), url.bucket(), url.ruta());
    }
}
