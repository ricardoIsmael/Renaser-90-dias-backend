package com.renaser.os.rag.infrastructure.adapter.out.persistence.memoria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface SpringDataRecuerdoRenasiaRepository extends JpaRepository<RecuerdoRenasiaJpaEntity, UUID> {

    List<RecuerdoRenasiaJpaEntity> findByParticipanteIdOrderByCreadoEnAsc(UUID participanteId);

    @Modifying
    @Query("DELETE FROM RecuerdoRenasiaJpaEntity r WHERE r.participanteId = :participanteId")
    void borrarDe(@Param("participanteId") UUID participanteId);

    /** Solo si es de esa persona: el id solo no alcanza para borrar. */
    @Modifying
    @Query("DELETE FROM RecuerdoRenasiaJpaEntity r WHERE r.id = :id AND r.participanteId = :participanteId")
    int borrarUno(@Param("participanteId") UUID participanteId, @Param("id") UUID id);
}
