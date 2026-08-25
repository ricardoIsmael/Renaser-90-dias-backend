package com.renaser.os.onboarding.infrastructure.adapter.in.rest.grabacionv90;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record RegistrarGrabacionV90Request(@NotBlank String phase, @NotBlank String axis,
                                            @PositiveOrZero short index, String questionKey,
                                            @NotNull Long mediaId, BigDecimal durationSeconds, String transcript) {
}
