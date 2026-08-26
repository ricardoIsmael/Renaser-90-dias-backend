package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface SpringDataHistorialCambioHorarioRepository extends JpaRepository<HistorialCambioHorarioJpaEntity, Long> {

    @Query("""
            SELECT DISTINCT h.habitoId FROM HistorialCambioHorarioJpaEntity h
            WHERE h.participanteId = :participanteId AND h.cambiadoEl >= :desde
            """)
    List<UUID> habitosDistintosDesde(@Param("participanteId") UUID participanteId, @Param("desde") LocalDate desde);
}
