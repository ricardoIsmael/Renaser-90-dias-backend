package com.renaser.os.points.infrastructure.adapter.out.persistence.puntaje;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface SpringDataPuntajeParticipanteRepository extends JpaRepository<PuntajeParticipanteJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PuntajeParticipanteJpaEntity p WHERE p.participanteId = :id")
    Optional<PuntajeParticipanteJpaEntity> findByIdParaEscritura(@Param("id") UUID id);
}
