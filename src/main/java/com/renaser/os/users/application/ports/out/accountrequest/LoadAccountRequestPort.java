package com.renaser.os.users.application.ports.out.accountrequest;

import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestStatus;
import com.renaser.os.users.domain.model.user.Email;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoadAccountRequestPort {

    Optional<AccountRequest> byId(AccountRequestId id);

    /**
     * ¿Hay ya una solicitud con este correo? Devuelve un booleano y no el agregado porque es lo
     * unico que el llamador necesita (ISP) y porque asi la consulta se resuelve dentro del
     * indice UNIQUE de {@code solicitudes_cuenta.email}, sin traer ni mapear la fila.
     */
    boolean existePorEmail(Email email);

    /** Para el rate limit de §5.3.6: 60/hora por IP. */
    long countSubmittedFromIpSince(String requestIp, Instant since);

    /**
     * Panel admin de solicitudes de cuenta (gap #9 de docs/PLAN_INTEGRACION_FRONTEND.md):
     * pagina de solicitudes, {@code statusFilter == null} = cualquier estado.
     */
    List<AccountRequest> pagina(AccountRequestStatus statusFilter, int page, int size);

    long contar(AccountRequestStatus statusFilter);
}
