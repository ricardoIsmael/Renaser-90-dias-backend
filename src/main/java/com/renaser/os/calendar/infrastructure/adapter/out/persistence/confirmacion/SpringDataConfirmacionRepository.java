package com.renaser.os.calendar.infrastructure.adapter.out.persistence.confirmacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataConfirmacionRepository extends JpaRepository<ConfirmacionEventoJpaEntity, ConfirmacionEventoId> {

    List<ConfirmacionEventoJpaEntity> findByUsuarioIdAndEventoIdIn(UUID usuarioId, List<UUID> eventoIds);

    List<ConfirmacionEventoJpaEntity> findByEventoIdAndInicioOcurrenciaInAndEstado(UUID eventoId,
                                                                                     List<Instant> ocurrencias,
                                                                                     EstadoConfirmacionJpa estado);
}
