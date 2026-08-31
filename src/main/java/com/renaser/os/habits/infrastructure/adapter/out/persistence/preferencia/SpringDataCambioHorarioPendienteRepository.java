package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataCambioHorarioPendienteRepository
        extends JpaRepository<CambioHorarioPendienteJpaEntity, PreferenciaHorarioPk> {

    Optional<CambioHorarioPendienteJpaEntity> findByParticipanteIdAndHabitoId(UUID participanteId, UUID habitoId);

    List<CambioHorarioPendienteJpaEntity> findByParticipanteId(UUID participanteId);

    /** Orden estable por participante para que el barrido nocturno sea reproducible en los logs. */
    List<CambioHorarioPendienteJpaEntity> findByFechaEfectivaLessThanEqualOrderByParticipanteIdAscHabitoIdAsc(
            LocalDate fecha);

    @Modifying
    @Query("DELETE FROM CambioHorarioPendienteJpaEntity c WHERE c.participanteId = :participanteId AND c.habitoId = :habitoId")
    void deleteByParticipanteIdAndHabitoId(@Param("participanteId") UUID participanteId,
                                            @Param("habitoId") UUID habitoId);
}
