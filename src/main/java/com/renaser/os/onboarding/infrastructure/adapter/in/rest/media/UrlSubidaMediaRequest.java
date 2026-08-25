package com.renaser.os.onboarding.infrastructure.adapter.in.rest.media;

import com.renaser.os.onboarding.domain.model.media.ClaseMedia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UrlSubidaMediaRequest(String flow, String questionKey, @NotNull ClaseMedia kind,
                                     @NotBlank String contentType) {
}
