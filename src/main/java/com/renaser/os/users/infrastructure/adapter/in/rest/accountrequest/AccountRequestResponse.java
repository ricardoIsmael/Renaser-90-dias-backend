package com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestStatus;
import com.renaser.os.users.domain.model.accountrequest.EleccionDeIngreso;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Proyeccion a mano del panel admin (gap #9) — nunca la entidad JPA serializada.
 *
 * <p>Spec 025 (aditivo): {@code reviewedByName} y, en una solicitud aceptada, {@code acceptance} con lo que se eligió (formaciones, duración, rol y
 * si fue con diagnóstico). Las aceptadas antes de las formaciones traen «Escuela, sin diagnóstico» y sin rol ni duración.
 */
public record AccountRequestResponse(UUID id, String email, String fullName, String phone, String city,
                                      AccountRequestStatus status, String rejectionReason, Instant createdAt,
                                      Instant reviewedAt, String reviewedByName, Acceptance acceptance) {

    /**
     * @param role           código del rol base ({@code null} en lo anterior a las formaciones); {@code durationDays}: solo el aprendiz
     * @param customRoleId   el rol personalizado con el que entró, si entró con uno (V142); {@code customRoleName}: cómo se llamaba ese día
     */
    public record Acceptance(List<String> formations, Integer durationDays, String role, UUID customRoleId, String customRoleName, boolean withDiagnosis) {

        /** La misma proyección la usa la ficha de la persona: lo aceptado se dice igual en la lista y en la ficha. */
        public static Acceptance from(EleccionDeIngreso eleccion) {
            return new Acceptance(eleccion.formaciones().stream().map(Formacion::codigo).toList(), eleccion.duracionElegidaDias(),
                    eleccion.rol() == null ? null : eleccion.rol().name(), eleccion.rolPersonalizadoId(), eleccion.rolPersonalizadoNombre(),
                    eleccion.conDiagnostico());
        }
    }

    public static AccountRequestResponse from(AccountRequest request, Map<UserId, String> nombresDeQuienDecidio) {
        String revisor = request.reviewedBy() == null ? null : nombresDeQuienDecidio.get(request.reviewedBy());
        Acceptance acceptance = request.eleccion() == null ? null : Acceptance.from(request.eleccion());
        return new AccountRequestResponse(request.id().value(), request.email().value(), request.fullName(),
                request.phone(), request.city(), request.status(), request.rejectionReason(), request.createdAt(),
                request.reviewedAt(), revisor, acceptance);
    }
}
