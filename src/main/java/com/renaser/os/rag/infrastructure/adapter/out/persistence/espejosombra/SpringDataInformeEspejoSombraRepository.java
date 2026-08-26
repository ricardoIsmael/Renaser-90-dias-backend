package com.renaser.os.rag.infrastructure.adapter.out.persistence.espejosombra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataInformeEspejoSombraRepository extends JpaRepository<InformeEspejoSombraJpaEntity, UUID> {

    Optional<InformeEspejoSombraJpaEntity> findByParticipanteIdAndSemanaInicio(UUID participanteId,
                                                                                 LocalDate semanaInicio);

    @Query("SELECT e FROM InformeEspejoSombraJpaEntity e WHERE e.participanteId = :participanteId "
            + "ORDER BY e.semanaInicio DESC")
    List<InformeEspejoSombraJpaEntity> deParticipante(@Param("participanteId") UUID participanteId);
}
