package com.renaser.os.onboarding.infrastructure.adapter.in.rest.estado;

import com.renaser.os.onboarding.domain.model.estado.HitoOnboarding;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * @param termsVersionId spec 027, opcional y aditivo: con el hito de Términos, la versión que la persona leyó. Sin ella (una app anterior) se anota la vigente de
 *                       su formación; si dejó de serlo mientras leía, 409.
 */
public record AceptarHitoRequest(@NotNull HitoOnboarding milestone, UUID termsVersionId) {
}
