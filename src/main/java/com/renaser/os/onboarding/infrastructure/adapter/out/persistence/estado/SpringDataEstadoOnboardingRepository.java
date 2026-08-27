package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.estado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataEstadoOnboardingRepository extends JpaRepository<EstadoOnboardingJpaEntity, UUID> {

    long countByCompletadoTrue();

    long countByPactoFirmadoEnIsNotNull();
}
