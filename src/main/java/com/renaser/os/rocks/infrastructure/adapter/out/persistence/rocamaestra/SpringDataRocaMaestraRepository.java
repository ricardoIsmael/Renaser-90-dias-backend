package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocamaestra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataRocaMaestraRepository extends JpaRepository<RocaMaestraJpaEntity, UUID> {

    List<RocaMaestraJpaEntity> findByParticipanteId(UUID participanteId);

    Optional<RocaMaestraJpaEntity> findByParticipanteIdAndEje(UUID participanteId, EjeObjetivoJpa eje);
}
