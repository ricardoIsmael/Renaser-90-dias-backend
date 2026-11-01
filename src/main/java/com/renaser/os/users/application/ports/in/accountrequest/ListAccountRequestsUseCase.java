package com.renaser.os.users.application.ports.in.accountrequest;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestStatus;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Panel admin de solicitudes de cuenta (gap #9 de docs/PLAN_INTEGRACION_FRONTEND.md): listado paginado. */
public interface ListAccountRequestsUseCase {

    PaginaAccountRequests listar(ListAccountRequestsCommand command);

    /**
     * @param formacion opcional (spec 025): con ella, las pendientes (que todavía no tienen formación) y las aceptadas en esa formación
     */
    record ListAccountRequestsCommand(UserId actorId, AccountRequestStatus statusFilter, Formacion formacion, int page, int size) {

        /** Sin filtro de formación: lo de siempre. */
        public ListAccountRequestsCommand(UserId actorId, AccountRequestStatus statusFilter, int page, int size) {
            this(actorId, statusFilter, null, page, size);
        }

        public ListAccountRequestsCommand {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            if (page < 0) {
                throw new IllegalArgumentException("page no puede ser negativo");
            }
            if (size <= 0 || size > 200) {
                throw new IllegalArgumentException("size debe estar entre 1 y 200");
            }
        }
    }

    /**
     * @param nombresDeQuienDecidio el nombre de quien aceptó o rechazó cada solicitud decidida, para decir «Aceptada por Ana» (spec 025, US4);
     *                              falta quien ya no existe
     */
    record PaginaAccountRequests(List<AccountRequest> contenido, long total, int page, int size,
                                 Map<UserId, String> nombresDeQuienDecidio) {
    }
}
