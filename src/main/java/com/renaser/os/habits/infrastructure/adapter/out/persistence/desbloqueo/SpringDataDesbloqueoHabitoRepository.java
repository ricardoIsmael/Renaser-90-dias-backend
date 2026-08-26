package com.renaser.os.habits.infrastructure.adapter.out.persistence.desbloqueo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataDesbloqueoHabitoRepository extends JpaRepository<DesbloqueoHabitoJpaEntity, DesbloqueoHabitoPk> {

    List<DesbloqueoHabitoJpaEntity> findByParticipanteId(UUID participanteId);
}
