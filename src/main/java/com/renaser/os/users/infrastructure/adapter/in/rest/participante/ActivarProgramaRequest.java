package com.renaser.os.users.infrastructure.adapter.in.rest.participante;

import java.time.LocalDate;

/**
 * Spec 036 (D-309): la persona ya no elige el Dia 1, asi que {@code startDate} es OPCIONAL (antes era obligatoria, D-66).
 * Ausente significa "mañana en tu zona". Si viene, es la fecha que la app mostro —la primera de
 * {@code GET /api/v1/onboarding/activate-program}— y tiene que seguir siendo mañana: si no, 400. Formato
 * {@code yyyy-MM-dd} (Jackson lo deserializa nativo contra {@link LocalDate}).
 */
public record ActivarProgramaRequest(LocalDate startDate) {
}
