package com.renaser.os.community.infrastructure.adapter.in.rest.cohorte;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de {@code PUT /api/v1/admin/cohorts/{id}/reception/guides}.
 *
 * <p>Es un reemplazo, no un alta: la lista que llega es la lista que queda. Mandar la misma dos
 * veces no cambia nada.
 *
 * @param receptionCellId dónde atienden. {@code null} conserva la que ya estaba configurada.
 */
public record ReceptionGuidesRequest(UUID receptionCellId, @NotNull List<GuideRef> guides) {

    /** Exactamente uno de los dos. Ambos o ninguno es un error, no una preferencia. */
    public record GuideRef(UUID userId, String email) {
    }
}
