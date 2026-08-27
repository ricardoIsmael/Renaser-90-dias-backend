package com.renaser.os.users.application.ports.out.accountrequest;

import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;

/** Panel admin de solicitudes de cuenta (gap #9): borrado, separado de `Save` (ISP, §5.4.8). */
public interface DeleteAccountRequestPort {

    /** {@code true} si existia y se borro; {@code false} si no existia (idempotente). */
    boolean deleteById(AccountRequestId id);
}
