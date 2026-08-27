package com.renaser.os.users.application.ports.in.accountrequest;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestStatus;

import java.util.List;
import java.util.Objects;

/** Panel admin de solicitudes de cuenta (gap #9 de docs/PLAN_INTEGRACION_FRONTEND.md): listado paginado. */
public interface ListAccountRequestsUseCase {

    PaginaAccountRequests listar(ListAccountRequestsCommand command);

    record ListAccountRequestsCommand(UserId actorId, AccountRequestStatus statusFilter, int page, int size) {

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

    record PaginaAccountRequests(List<AccountRequest> contenido, long total, int page, int size) {
    }
}
