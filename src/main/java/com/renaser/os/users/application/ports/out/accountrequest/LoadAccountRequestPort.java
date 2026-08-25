package com.renaser.os.users.application.ports.out.accountrequest;

import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;

import java.time.Instant;
import java.util.Optional;

public interface LoadAccountRequestPort {

    Optional<AccountRequest> byId(AccountRequestId id);

    /** Para el rate limit de §5.3.6: 60/hora por IP. */
    long countSubmittedFromIpSince(String requestIp, Instant since);
}
