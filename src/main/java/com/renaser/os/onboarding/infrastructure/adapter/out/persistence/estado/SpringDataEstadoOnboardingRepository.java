package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.estado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataEstadoOnboardingRepository extends JpaRepository<EstadoOnboardingJpaEntity, UUID> {

    long countByCompletadoTrue();

    long countByPactoFirmadoEnIsNotNull();

    /* Las mismas, sin unas cuentas: las de prueba de «ver como» (D-273). */
    long countByUsuarioIdNotIn(java.util.Collection<UUID> sinContar);

    long countByCompletadoTrueAndUsuarioIdNotIn(java.util.Collection<UUID> sinContar);

    long countByPactoFirmadoEnIsNotNullAndUsuarioIdNotIn(java.util.Collection<UUID> sinContar);
}
