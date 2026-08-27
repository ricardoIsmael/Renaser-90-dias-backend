package com.renaser.os.users.infrastructure.adapter.out.persistence.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByEmail(String email);

    /** Candidatas a purga del cron de bajas de cuenta (AccountDeletionService). */
    List<UserJpaEntity> findByBajaSolicitadaEnNotNullAndBajaSolicitadaEnLessThanEqual(Instant corte);

    List<UserJpaEntity> findByRolInAndEstado(Collection<RolUsuarioJpa> roles, EstadoUsuarioJpa estado,
                                              Pageable pageable);

    List<UserJpaEntity> findByRolIn(Collection<RolUsuarioJpa> roles, Pageable pageable);

    long countByRolInAndEstado(Collection<RolUsuarioJpa> roles, EstadoUsuarioJpa estado);

    long countByRolIn(Collection<RolUsuarioJpa> roles);
}
