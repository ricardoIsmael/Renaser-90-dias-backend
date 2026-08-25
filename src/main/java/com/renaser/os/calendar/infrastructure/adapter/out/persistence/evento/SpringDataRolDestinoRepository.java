package com.renaser.os.calendar.infrastructure.adapter.out.persistence.evento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface SpringDataRolDestinoRepository extends JpaRepository<RolDestinoEventoJpaEntity, RolDestinoEventoId> {

    List<RolDestinoEventoJpaEntity> findByEventoId(UUID eventoId);

    List<RolDestinoEventoJpaEntity> findByEventoIdIn(List<UUID> eventoIds);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RolDestinoEventoJpaEntity r WHERE r.eventoId = :eventoId")
    void deleteByEventoId(@Param("eventoId") UUID eventoId);
}
