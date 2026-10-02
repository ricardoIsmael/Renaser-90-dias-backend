package com.renaser.os.users.infrastructure.adapter.out.persistence.emergencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataSolicitudDeEmergenciaRepository extends JpaRepository<SolicitudDeEmergenciaJpaEntity, UUID> {

    /** Apoyado en el índice único parcial `solicitudes_emergencia_una_abierta_uk`: hay a lo sumo una. */
    Optional<SolicitudDeEmergenciaJpaEntity> findFirstByAprendizIdAndEstado(UUID aprendizId, String estado);
}
