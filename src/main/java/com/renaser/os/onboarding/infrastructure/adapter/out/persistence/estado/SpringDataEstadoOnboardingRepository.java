package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.estado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataEstadoOnboardingRepository extends JpaRepository<EstadoOnboardingJpaEntity, UUID> {

    /** Las que de verdad empezaron: una fila puede nacer antes (la ficha o el pago que Administración anota al aceptar, specs 026 y 032). */
    long countByIniciadoEnIsNotNull();

    long countByIniciadoEnIsNotNullAndUsuarioIdNotIn(java.util.Collection<UUID> sinContar);

    long countByCompletadoTrue();

    long countByPactoFirmadoEnIsNotNull();

    /* Las mismas, sin unas cuentas: las de prueba de «ver como» (D-273). */
    long countByCompletadoTrueAndUsuarioIdNotIn(java.util.Collection<UUID> sinContar);

    long countByPactoFirmadoEnIsNotNullAndUsuarioIdNotIn(java.util.Collection<UUID> sinContar);
}
