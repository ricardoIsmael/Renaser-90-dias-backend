package com.renaser.os.users.application.ports.out.accountrequest;

import com.renaser.os.users.domain.model.accountrequest.AccountRequest;

public interface SaveAccountRequestPort {

    AccountRequest save(AccountRequest accountRequest);
}
