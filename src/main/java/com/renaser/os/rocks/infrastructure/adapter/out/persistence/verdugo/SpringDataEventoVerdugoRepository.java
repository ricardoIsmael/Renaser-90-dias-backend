package com.renaser.os.rocks.infrastructure.adapter.out.persistence.verdugo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataEventoVerdugoRepository extends JpaRepository<EventoVerdugoJpaEntity, UUID> {

    List<EventoVerdugoJpaEntity> findByParticipanteIdOrderByDisparadoEnDesc(UUID participanteId);

    @Query("SELECT e FROM EventoVerdugoJpaEntity e WHERE e.resultado IS NULL AND e.disparadoEn >= :desde AND e.disparadoEn < :hasta")
    List<EventoVerdugoJpaEntity> findPendientesEntre(@Param("desde") Instant desde, @Param("hasta") Instant hasta);
}
