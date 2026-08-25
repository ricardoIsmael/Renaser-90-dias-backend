package com.renaser.os.calendar.infrastructure.adapter.out.persistence.evento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataExcepcionRepository extends JpaRepository<ExcepcionEventoJpaEntity, UUID> {

    List<ExcepcionEventoJpaEntity> findByEventoId(UUID eventoId);

    List<ExcepcionEventoJpaEntity> findByEventoIdIn(List<UUID> eventoIds);

    Optional<ExcepcionEventoJpaEntity> findByEventoIdAndInicioOcurrencia(UUID eventoId, java.time.Instant inicioOcurrencia);
}
