package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.renaser.os.habits.application.ports.in.registro.SolicitarUrlEvidenciaRegistroUseCase.UrlEvidenciaRegistro;

/**
 * {@code ruta} es lo que el cliente devuelve despues en
 * {@code POST /api/v1/habit-tracks/{id}/evidence} — nunca {@code uploadUrl}, que lleva firma
 * y vencimiento (mismo criterio que {@code UrlAdjuntoRachaResponse} y el muro).
 */
public record UrlEvidenciaRegistroResponse(String uploadUrl, String bucket, String ruta) {

    public static UrlEvidenciaRegistroResponse from(UrlEvidenciaRegistro url) {
        return new UrlEvidenciaRegistroResponse(url.url().toString(), url.bucket(), url.ruta());
    }
}
