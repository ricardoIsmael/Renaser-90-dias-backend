package com.renaser.os.habits.infrastructure.adapter.out.persistence.espiritu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataRegistroEspirituRepository extends JpaRepository<RegistroEspirituJpaEntity, UUID> {

    Optional<RegistroEspirituJpaEntity> findByParticipanteIdAndDia(UUID participanteId, short dia);

    Optional<RegistroEspirituJpaEntity> findFirstByParticipanteIdOrderByDiaDesc(UUID participanteId);

    List<RegistroEspirituJpaEntity> findByParticipanteId(UUID participanteId);
}
