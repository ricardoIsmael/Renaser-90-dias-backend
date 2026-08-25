package com.renaser.os.onboarding.infrastructure.adapter.in.rest.media;

import com.renaser.os.onboarding.domain.model.media.MediaOnboarding;

import java.math.BigDecimal;
import java.time.Instant;

public record MediaResponse(Long id, String flow, String questionKey, String kind, String bucket, String path,
                             String mime, Long sizeBytes, BigDecimal durationSeconds, String metadata,
                             Instant createdAt) {

    public static MediaResponse from(MediaOnboarding m) {
        return new MediaResponse(m.id(), m.flujo(), m.clavePregunta(), m.clase().name(), m.bucket(), m.rutaStorage(),
                m.mime(), m.tamanoBytes(), m.duracionSegundos(), m.metadatos(), m.creadoEn());
    }
}
