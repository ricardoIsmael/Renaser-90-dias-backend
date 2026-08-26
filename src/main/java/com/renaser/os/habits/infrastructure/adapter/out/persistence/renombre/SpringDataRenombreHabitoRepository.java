package com.renaser.os.habits.infrastructure.adapter.out.persistence.renombre;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface SpringDataRenombreHabitoRepository extends JpaRepository<RenombreHabitoJpaEntity, RenombreHabitoPk> {

    Optional<RenombreHabitoJpaEntity> findByParticipanteIdAndHabitoId(UUID participanteId, UUID habitoId);

    @Modifying
    @Query("DELETE FROM RenombreHabitoJpaEntity r WHERE r.participanteId = :participanteId AND r.habitoId = :habitoId")
    void deleteByParticipanteIdAndHabitoId(@Param("participanteId") UUID participanteId,
                                            @Param("habitoId") UUID habitoId);
}
