package com.renaser.os.rag.infrastructure.adapter.out.persistence.propuesta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataPropuestaAccionRepository extends JpaRepository<PropuestaAccionJpaEntity, UUID> {

    @Query("SELECT p FROM PropuestaAccionJpaEntity p WHERE p.participanteId = :participanteId "
            + "AND p.estado = 'PENDIENTE' AND p.creadaEn >= :desde ORDER BY p.creadaEn ASC")
    List<PropuestaAccionJpaEntity> pendientesCreadasDesde(@Param("participanteId") UUID participanteId,
                                                          @Param("desde") Instant desde);
}
