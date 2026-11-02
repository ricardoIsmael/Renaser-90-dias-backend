package com.renaser.os.onboarding.infrastructure.adapter.in.rest.estado;

import com.renaser.os.onboarding.domain.model.estado.HitoOnboarding;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * @param termsVersionId spec 027, opcional y aditivo: con el hito de Términos, la versión que la persona leyó. Sin ella (una app anterior) se anota la vigente de
 *                       su formación; si dejó de serlo mientras leía, 409.
 * @param agreementStartDate spec 039, opcional y aditivo: con el hito de Términos, el inicio de su programa que vio en «Mi aceptación» ({@code yyyy-MM-dd}). Si ya no es el
 *                       de ahora (pasó la medianoche), 409.
 * @param scheduleFingerprint spec 039, opcional y aditivo: con el hito de Términos, la huella del cronograma de pagos que vio y aceptó al firmar. Si ya no es la vigente,
 *                       409; si coincide, queda confirmado el cronograma (la confirmación de la spec 032) en la misma transacción.
 */
public record AceptarHitoRequest(@NotNull HitoOnboarding milestone, UUID termsVersionId, LocalDate agreementStartDate, String scheduleFingerprint) {
}
