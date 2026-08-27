package com.renaser.os.users.application.ports.out.accountrequest;

import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoadAccountRequestPort {

    Optional<AccountRequest> byId(AccountRequestId id);

    /** Para el rate limit de §5.3.6: 60/hora por IP. */
    long countSubmittedFromIpSince(String requestIp, Instant since);

    /**
     * Panel admin de solicitudes de cuenta (gap #9 de docs/PLAN_INTEGRACION_FRONTEND.md):
     * pagina de solicitudes, {@code statusFilter == null} = cualquier estado.
     */
    List<AccountRequest> pagina(AccountRequestStatus statusFilter, int page, int size);

    long contar(AccountRequestStatus statusFilter);
}
