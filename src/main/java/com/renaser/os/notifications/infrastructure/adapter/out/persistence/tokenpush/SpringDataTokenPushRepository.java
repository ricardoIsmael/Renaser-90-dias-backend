package com.renaser.os.notifications.infrastructure.adapter.out.persistence.tokenpush;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataTokenPushRepository extends JpaRepository<TokenPushJpaEntity, UUID> {

    Optional<TokenPushJpaEntity> findByToken(String token);

    List<TokenPushJpaEntity> findByUsuarioId(UUID usuarioId);
}
