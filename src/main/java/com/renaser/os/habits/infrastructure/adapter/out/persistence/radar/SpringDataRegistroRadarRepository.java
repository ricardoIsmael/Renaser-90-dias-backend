package com.renaser.os.habits.infrastructure.adapter.out.persistence.radar;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataRegistroRadarRepository extends JpaRepository<RegistroRadarJpaEntity, UUID> {

    Optional<RegistroRadarJpaEntity> findTopByParticipanteIdOrderByCreadoEnDesc(UUID participanteId);

    List<RegistroRadarJpaEntity> findByParticipanteIdOrderByCreadoEnDesc(UUID participanteId, Pageable pageable);

    List<RegistroRadarJpaEntity> findByParticipanteIdAndCreadoEnLessThanOrderByCreadoEnDesc(
            UUID participanteId, Instant cursor, Pageable pageable);

    /** Para {@link com.renaser.os.users.api.RadarLogrosFinder} — agregacion en SQL, nunca en memoria. */
    long countByParticipanteId(UUID participanteId);

    /** {@code null} (via {@code Optional} en el adaptador) si el participante nunca hizo un check-in. */
    @Query("SELECT MIN(r.creadoEn) FROM RegistroRadarJpaEntity r WHERE r.participanteId = :participanteId")
    Instant minCreadoEnPorParticipante(@Param("participanteId") UUID participanteId);
}
