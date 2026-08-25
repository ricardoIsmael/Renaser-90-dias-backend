package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.media;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataMediaOnboardingRepository extends JpaRepository<MediaOnboardingJpaEntity, Long> {

    Optional<MediaOnboardingJpaEntity> findByIdAndUsuarioId(Long id, UUID usuarioId);
}
