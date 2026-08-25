package com.renaser.os.users.infrastructure.adapter.out.persistence.user;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class UserPersistenceAdapter implements LoadUserPort, SaveUserPort {

    private final SpringDataUserRepository repository;
    private final UserPersistenceMapper mapper;

    UserPersistenceAdapter(SpringDataUserRepository repository, UserPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<User> byId(UserId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<User> byEmail(Email email) {
        return repository.findByEmail(email.value()).map(mapper::toDomain);
    }

    /** `findAllById` de Spring Data: un solo `WHERE id IN (...)`, no una consulta por id. */
    @Override
    public List<User> byIds(Collection<UserId> ids) {
        List<UUID> valores = ids.stream().map(UserId::value).toList();
        return repository.findAllById(valores).stream().map(mapper::toDomain).toList();
    }

    @Override
    public User save(User user) {
        var saved = repository.save(mapper.toEntity(user));
        return mapper.toDomain(saved);
    }
}
