package com.renaser.os.habits.infrastructure.adapter.out.persistence.guia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface SpringDataAdjuntoGuiaRepository extends JpaRepository<AdjuntoGuiaJpaEntity, UUID> {

    List<AdjuntoGuiaJpaEntity> findByGuiaIdIn(Collection<UUID> guiaIds);
}
