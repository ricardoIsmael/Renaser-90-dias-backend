package com.renaser.os.community.infrastructure.adapter.in.rest.cohorte;

import com.renaser.os.community.application.ports.in.acompanamiento.ConfigurarMentoriaUseCase.GuiaVigente;
import com.renaser.os.community.application.ports.in.acompanamiento.ConfigurarMentoriaUseCase.GuiasVigentes;

import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/v1/admin/cohorts/{id}/reception/guides} (D-242): los guías vigentes de la
 * recepción, con nombre, rol y estado de su cuenta.
 *
 * <p>No reusa {@code ReceptionGuidesResponse} (la del PUT, que solo trae ids): para leer hace
 * falta el nombre, y cambiarle la forma a la respuesta del PUT rompería a quien ya la consume.
 * Sin correo a propósito: {@code UserSummary} no lo trae y la pantalla no lo necesita.
 *
 * @param receptionCellId {@code null} si la cohorte todavía no tiene grupo de recepción designado.
 */
public record ReceptionGuidesListResponse(UUID cohortId, UUID receptionCellId, List<Guide> guides) {

    public static ReceptionGuidesListResponse from(GuiasVigentes guias) {
        return new ReceptionGuidesListResponse(guias.cohorteId(), guias.celulaRecepcionId(),
                guias.guias().stream().map(Guide::from).toList());
    }

    /** {@code role} y {@code status} tal como el enum; {@code null} si la cuenta ya no existe. */
    public record Guide(UUID userId, String fullName, String role, String status) {

        static Guide from(GuiaVigente guia) {
            return new Guide(guia.usuarioId(), guia.nombre(), guia.rol(), guia.estado());
        }
    }
}
