package com.renaser.os.habits.infrastructure.adapter.out.persistence.santuario;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataRachaSinCelularRepository extends JpaRepository<RachaSinCelularJpaEntity, UUID> {

    Optional<RachaSinCelularJpaEntity> findByParticipanteIdAndEstado(UUID participanteId, EstadoRachaJpa estado);

    /**
     * Bloqueo pesimista para cerrar/romper una racha: ambos caminos otorgan o penalizan
     * puntos, y sin lock dos requests concurrentes leen la misma racha ACTIVA y ambas
     * pagan. El indice unico parcial `rachas_viva_uk` impide DOS rachas activas a la vez,
     * pero no protege contra dos cierres de la MISMA racha.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RachaSinCelularJpaEntity r WHERE r.participanteId = :participanteId AND r.estado = :estado")
    Optional<RachaSinCelularJpaEntity> findActivaParaEscritura(@Param("participanteId") UUID participanteId,
                                                                @Param("estado") EstadoRachaJpa estado);

    List<RachaSinCelularJpaEntity> findByParticipanteIdInAndEstado(List<UUID> participanteIds, EstadoRachaJpa estado);
}
