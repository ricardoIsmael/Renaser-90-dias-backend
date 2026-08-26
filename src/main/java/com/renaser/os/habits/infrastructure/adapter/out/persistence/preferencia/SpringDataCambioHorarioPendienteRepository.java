package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface SpringDataCambioHorarioPendienteRepository
        extends JpaRepository<CambioHorarioPendienteJpaEntity, PreferenciaHorarioPk> {

    Optional<CambioHorarioPendienteJpaEntity> findByParticipanteIdAndHabitoId(UUID participanteId, UUID habitoId);

    @Modifying
    @Query("DELETE FROM CambioHorarioPendienteJpaEntity c WHERE c.participanteId = :participanteId AND c.habitoId = :habitoId")
    void deleteByParticipanteIdAndHabitoId(@Param("participanteId") UUID participanteId,
                                            @Param("habitoId") UUID habitoId);
}
