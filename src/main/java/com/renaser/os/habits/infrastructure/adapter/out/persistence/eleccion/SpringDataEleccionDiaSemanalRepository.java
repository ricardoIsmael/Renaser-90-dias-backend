package com.renaser.os.habits.infrastructure.adapter.out.persistence.eleccion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface SpringDataEleccionDiaSemanalRepository
        extends JpaRepository<EleccionDiaSemanalJpaEntity, EleccionDiaSemanalPk> {

    List<EleccionDiaSemanalJpaEntity> findByParticipanteIdAndHabitoIdAndSemanaInicio(UUID participanteId,
                                                                                       UUID habitoId,
                                                                                       LocalDate semanaInicio);

    @Modifying
    @Query("""
            DELETE FROM EleccionDiaSemanalJpaEntity e
            WHERE e.participanteId = :participanteId AND e.habitoId = :habitoId AND e.semanaInicio = :semanaInicio
            """)
    void deleteByParticipanteIdAndHabitoIdAndSemanaInicio(@Param("participanteId") UUID participanteId,
                                                            @Param("habitoId") UUID habitoId,
                                                            @Param("semanaInicio") LocalDate semanaInicio);
}
