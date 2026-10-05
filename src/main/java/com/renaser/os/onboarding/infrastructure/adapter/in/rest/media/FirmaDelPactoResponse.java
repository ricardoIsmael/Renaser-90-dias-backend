package com.renaser.os.onboarding.infrastructure.adapter.in.rest.media;

import com.renaser.os.onboarding.application.ports.in.media.VerFirmaDelPactoUseCase.FirmaParaVer;

import java.time.Instant;

/**
 * {@code GET /api/v1/onboarding/pact/signature} (D-253). Solo la URL de lectura y su vencimiento: ni bucket,
 * ni ruta, ni {@code mediaId} como campos propios (la ruta va dentro de la URL prefirmada, como en
 * {@code mediaUrl} del chat, y es del propio usuario).
 */
public record FirmaDelPactoResponse(String url, Instant expiresAt) {

    public static FirmaDelPactoResponse from(FirmaParaVer firma) {
        return new FirmaDelPactoResponse(firma.url().toString(), firma.venceEn());
    }
}
