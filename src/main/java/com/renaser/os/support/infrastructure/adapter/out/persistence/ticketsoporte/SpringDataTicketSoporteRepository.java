package com.renaser.os.support.infrastructure.adapter.out.persistence.ticketsoporte;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataTicketSoporteRepository extends JpaRepository<TicketSoporteJpaEntity, UUID> {

    List<TicketSoporteJpaEntity> findByUsuarioIdOrderByCreadoEnDesc(UUID usuarioId);

    List<TicketSoporteJpaEntity> findByEstadoOrderByCreadoEnDesc(EstadoTicketSoporteJpa estado);

    List<TicketSoporteJpaEntity> findByOrderByCreadoEnDesc();
}
