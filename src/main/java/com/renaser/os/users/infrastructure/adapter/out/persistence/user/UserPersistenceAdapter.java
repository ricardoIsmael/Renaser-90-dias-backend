package com.renaser.os.users.infrastructure.adapter.out.persistence.user;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.out.user.DeleteUserPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class UserPersistenceAdapter implements LoadUserPort, SaveUserPort, DeleteUserPort {

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

    /** Panel admin de staff (gap #6). `statusFilter == null` = cualquier estado. */
    @Override
    public List<User> byRoles(Collection<UserRole> roles, UserStatus statusFilter, int page, int size) {
        List<RolUsuarioJpa> rolesJpa = roles.stream().map(mapper::toJpaRolePublic).toList();
        var pageable = PageRequest.of(page, size, Sort.by("nombreCompleto"));
        var entidades = statusFilter == null
                ? repository.findByRolIn(rolesJpa, pageable)
                : repository.findByRolInAndEstado(rolesJpa, mapper.toJpaStatusPublic(statusFilter), pageable);
        return entidades.stream().map(mapper::toDomain).toList();
    }

    @Override
    public long countByRoles(Collection<UserRole> roles, UserStatus statusFilter) {
        List<RolUsuarioJpa> rolesJpa = roles.stream().map(mapper::toJpaRolePublic).toList();
        return statusFilter == null
                ? repository.countByRolIn(rolesJpa)
                : repository.countByRolInAndEstado(rolesJpa, mapper.toJpaStatusPublic(statusFilter));
    }

    /**
     * {@code saveAndFlush}, no {@code save} (2026-08-27): {@code AccountRequestService.submit}
     * crea el usuario y, en la MISMA transaccion, escribe su credencial con una query nativa
     * ({@code CredencialPersistenceAdapter.actualizarHash}). Una query nativa bypasea el
     * contexto de persistencia — Hibernate no sabe que tabla toca un SQL crudo, asi que no
     * garantiza vaciar antes el INSERT pendiente. Sin el flush explicito, esa fila podria
     * no existir todavia en la base cuando corre el UPDATE, y {@code actualizarHash}
     * fallaria con 0 filas afectadas pese a que el usuario "ya se guardo". El costo es un
     * viaje a la base que de todas formas iba a pasar al hacer commit — no un round-trip
     * nuevo.
     */
    @Override
    public User save(User user) {
        var saved = repository.saveAndFlush(mapper.toEntity(user));
        return mapper.toDomain(saved);
    }

    @Override
    public List<UserId> pendingDeletionUpTo(Instant corte) {
        return repository.findByBajaSolicitadaEnNotNullAndBajaSolicitadaEnLessThanEqual(corte).stream()
                .map(e -> UserId.of(e.getId())).toList();
    }

    /** Hard delete real: las ~30 FK contra `usuarios` en el baseline son ON DELETE CASCADE
     * (o SET NULL en las de auditoria) - este DELETE solo se encarga de la fila raiz.
     * {@code existsById} primero porque el `deleteById` de Spring Data (`SimpleJpaRepository`)
     * lanza {@code EmptyResultDataAccessException} si el id ya no existe — idempotencia
     * real (contrato del puerto), no la que da por default Spring Data. */
    @Override
    public void deleteById(UserId id) {
        if (repository.existsById(id.value())) {
            repository.deleteById(id.value());
        }
    }
}
