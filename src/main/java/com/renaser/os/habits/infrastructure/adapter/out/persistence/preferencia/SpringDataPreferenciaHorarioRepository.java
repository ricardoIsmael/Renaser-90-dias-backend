package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataPreferenciaHorarioRepository
        extends JpaRepository<PreferenciaHorarioJpaEntity, PreferenciaHorarioPk> {

    Optional<PreferenciaHorarioJpaEntity> findByParticipanteIdAndHabitoId(UUID participanteId, UUID habitoId);
}
