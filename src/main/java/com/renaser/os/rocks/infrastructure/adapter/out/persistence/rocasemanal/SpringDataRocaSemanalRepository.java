package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocasemanal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataRocaSemanalRepository extends JpaRepository<RocaSemanalJpaEntity, UUID> {

    List<RocaSemanalJpaEntity> findByRocaMaestraIdInAndNumeroSemana(List<UUID> rocaMaestraIds, Short numeroSemana);

    Optional<RocaSemanalJpaEntity> findByRocaMaestraIdAndNumeroSemana(UUID rocaMaestraId, Short numeroSemana);
}
