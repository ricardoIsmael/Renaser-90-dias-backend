package com.renaser.os.community.infrastructure.adapter.out.persistence.cohorte;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataCohorteRepository extends JpaRepository<CohorteJpaEntity, UUID> {

    /* Sin la cohorte de prueba de «ver como» (D-273): `CohortePersistenceAdapter.listar`. */
    List<CohorteJpaEntity> findByEstadoAndIdNotOrderByCreadoEnDesc(EstadoCohorteJpa estado, UUID excluida);

    List<CohorteJpaEntity> findByIdNotOrderByCreadoEnDesc(UUID excluida);
}
