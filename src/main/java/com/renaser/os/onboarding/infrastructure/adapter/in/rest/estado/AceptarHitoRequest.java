package com.renaser.os.onboarding.infrastructure.adapter.in.rest.estado;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.renaser.os.onboarding.domain.model.estado.HitoOnboarding;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * @param termsVersionId spec 027, opcional y aditivo: con el hito de Términos, la versión que la persona leyó. Sin ella (una app anterior) se anota la vigente de
 *                       su formación; si dejó de serlo mientras leía, 409.
 * @param agreementStartDate spec 039, opcional y aditivo: con el hito de Términos, el inicio de su programa que vio en «Mi aceptación» ({@code yyyy-MM-dd}). Si ya no es el
 *                       de ahora (pasó la medianoche), 409.
 *
 * <p>D-315 (2026-11-02): el cronograma de pagos ya no se firma con los términos, así que ya no hay {@code scheduleFingerprint}. Una app de la spec 039 que todavía lo mande
 * no falla: el campo desconocido se ignora y la aceptación es la de siempre (el cronograma se confirma en «Mi ingreso»).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AceptarHitoRequest(@NotNull HitoOnboarding milestone, UUID termsVersionId, LocalDate agreementStartDate) {
}
