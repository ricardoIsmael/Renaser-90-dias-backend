package com.renaser.os.community.infrastructure.adapter.out.persistence.celula;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataCelulaRepository extends JpaRepository<CelulaJpaEntity, UUID> {

    List<CelulaJpaEntity> findByCohorteIdOrderByNombreAsc(UUID cohorteId);

    List<CelulaJpaEntity> findAllByOrderByNombreAsc();

    Optional<CelulaJpaEntity> findByMentorId(UUID mentorId);
}
