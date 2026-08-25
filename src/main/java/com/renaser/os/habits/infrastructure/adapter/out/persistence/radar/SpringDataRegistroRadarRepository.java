package com.renaser.os.habits.infrastructure.adapter.out.persistence.radar;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataRegistroRadarRepository extends JpaRepository<RegistroRadarJpaEntity, UUID> {

    Optional<RegistroRadarJpaEntity> findTopByParticipanteIdOrderByCreadoEnDesc(UUID participanteId);

    List<RegistroRadarJpaEntity> findByParticipanteIdOrderByCreadoEnDesc(UUID participanteId, Pageable pageable);

    List<RegistroRadarJpaEntity> findByParticipanteIdAndCreadoEnLessThanOrderByCreadoEnDesc(
            UUID participanteId, Instant cursor, Pageable pageable);
}
