package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocadiaria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface SpringDataRocaDiariaRepository extends JpaRepository<RocaDiariaJpaEntity, UUID> {

    List<RocaDiariaJpaEntity> findByParticipanteIdAndFecha(UUID participanteId, LocalDate fecha);

    int countByParticipanteIdAndFecha(UUID participanteId, LocalDate fecha);
}
