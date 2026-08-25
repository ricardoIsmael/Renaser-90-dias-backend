package com.renaser.os.community.infrastructure.adapter.out.persistence.cohorte;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataCohorteRepository extends JpaRepository<CohorteJpaEntity, UUID> {

    List<CohorteJpaEntity> findByEstadoOrderByCreadoEnDesc(EstadoCohorteJpa estado);

    List<CohorteJpaEntity> findAllByOrderByCreadoEnDesc();
}
