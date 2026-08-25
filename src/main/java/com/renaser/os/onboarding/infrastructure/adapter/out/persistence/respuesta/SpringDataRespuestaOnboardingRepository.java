package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.respuesta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataRespuestaOnboardingRepository extends JpaRepository<RespuestaOnboardingJpaEntity, Long> {

    Optional<RespuestaOnboardingJpaEntity> findByUsuarioIdAndPreguntaId(UUID usuarioId, Integer preguntaId);

    List<RespuestaOnboardingJpaEntity> findByUsuarioId(UUID usuarioId);
}
