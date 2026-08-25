package com.renaser.os.onboarding.infrastructure.adapter.in.rest.media;

import com.renaser.os.onboarding.domain.model.media.ClaseMedia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RegistrarMediaRequest(String flow, String questionKey, @NotNull ClaseMedia kind,
                                     @NotBlank String bucket, @NotBlank String path, String mime, Long sizeBytes,
                                     BigDecimal durationSeconds, String metadata) {
}
