package com.renaser.os.rag.infrastructure.adapter.out.persistence.agenda;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface SpringDataAgendaOcupadaRepository extends JpaRepository<AgendaOcupadaJpaEntity, UUID> {

    List<AgendaOcupadaJpaEntity> findByParticipanteId(UUID participanteId);

    @Modifying
    @Query("DELETE FROM AgendaOcupadaJpaEntity a WHERE a.participanteId = :participanteId")
    void borrarDe(@Param("participanteId") UUID participanteId);
}
