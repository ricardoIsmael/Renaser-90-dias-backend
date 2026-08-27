package com.renaser.os.habits.infrastructure.adapter.out.persistence.guia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataGuiaHabitoRepository extends JpaRepository<GuiaHabitoJpaEntity, UUID> {

    List<GuiaHabitoJpaEntity> findByHabitoIdIn(Collection<UUID> habitoIds);

    List<GuiaHabitoJpaEntity> findByHabitoId(UUID habitoId);

    /** La guia abierta (sin dia de cierre) mas reciente de un habito — a cerrar por {@code closePrevious}. */
    Optional<GuiaHabitoJpaEntity> findFirstByHabitoIdAndDiaFinIsNullOrderByDiaInicioDesc(UUID habitoId);
}
