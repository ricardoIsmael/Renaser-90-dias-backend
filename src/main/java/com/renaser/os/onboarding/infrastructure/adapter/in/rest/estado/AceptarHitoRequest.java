package com.renaser.os.onboarding.infrastructure.adapter.in.rest.estado;

import com.renaser.os.onboarding.domain.model.estado.HitoOnboarding;
import jakarta.validation.constraints.NotNull;

public record AceptarHitoRequest(@NotNull HitoOnboarding milestone) {
}
