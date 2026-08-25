package com.renaser.os.users.infrastructure.adapter.out.persistence.accountrequest;

import com.renaser.os.users.application.ports.out.accountrequest.LoadAccountRequestPort;
import com.renaser.os.users.application.ports.out.accountrequest.SaveAccountRequestPort;
import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
class AccountRequestPersistenceAdapter implements LoadAccountRequestPort, SaveAccountRequestPort {

    private final SpringDataAccountRequestRepository repository;
    private final AccountRequestPersistenceMapper mapper;

    AccountRequestPersistenceAdapter(SpringDataAccountRequestRepository repository,
                                      AccountRequestPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<AccountRequest> byId(AccountRequestId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public long countSubmittedFromIpSince(String requestIp, Instant since) {
        return repository.countByIpSolicitudAndCreadoEnAfter(requestIp, since);
    }

    @Override
    public AccountRequest save(AccountRequest accountRequest) {
        var saved = repository.save(mapper.toEntity(accountRequest));
        return mapper.toDomain(saved);
    }
}
