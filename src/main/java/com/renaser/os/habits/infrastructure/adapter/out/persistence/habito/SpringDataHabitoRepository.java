package com.renaser.os.habits.infrastructure.adapter.out.persistence.habito;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataHabitoRepository extends JpaRepository<HabitoJpaEntity, UUID> {

    List<HabitoJpaEntity> findByAmbitoAndActivoTrue(AmbitoHabitoJpa ambito);

    List<HabitoJpaEntity> findByAmbito(AmbitoHabitoJpa ambito);

    List<HabitoJpaEntity> findByAmbitoAndParticipanteIdAndActivoTrue(AmbitoHabitoJpa ambito, UUID participanteId);

    List<HabitoJpaEntity> findByIdIn(java.util.Collection<UUID> ids);

    java.util.Optional<HabitoJpaEntity> findByClaveSistema(String claveSistema);
}
