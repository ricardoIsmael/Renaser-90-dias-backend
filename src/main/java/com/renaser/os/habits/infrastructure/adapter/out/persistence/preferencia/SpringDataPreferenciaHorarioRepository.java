package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataPreferenciaHorarioRepository
        extends JpaRepository<PreferenciaHorarioJpaEntity, PreferenciaHorarioPk> {

    Optional<PreferenciaHorarioJpaEntity> findByParticipanteIdAndHabitoId(UUID participanteId, UUID habitoId);

    java.util.List<PreferenciaHorarioJpaEntity> findByParticipanteIdAndHabitoIdIn(UUID participanteId,
                                                                                    java.util.Collection<UUID> habitoIds);

    /** H-01: la preferencia general de UN habito para varias personas, en una consulta. */
    java.util.List<PreferenciaHorarioJpaEntity> findByParticipanteIdInAndHabitoId(java.util.Collection<UUID> participanteIds,
                                                                                    UUID habitoId);
}
