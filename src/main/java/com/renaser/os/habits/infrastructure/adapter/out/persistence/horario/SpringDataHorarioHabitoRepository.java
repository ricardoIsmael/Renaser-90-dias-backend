package com.renaser.os.habits.infrastructure.adapter.out.persistence.horario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataHorarioHabitoRepository extends JpaRepository<HorarioHabitoJpaEntity, UUID> {

    List<HorarioHabitoJpaEntity> findByHabitoId(UUID habitoId);
}
