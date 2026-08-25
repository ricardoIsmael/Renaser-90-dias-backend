package com.renaser.os.calendar.infrastructure.adapter.out.persistence.evento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface SpringDataRecurrenciaRepository extends JpaRepository<RecurrenciaJpaEntity, UUID> {

    /** clearAutomatically=true: dentro de guardar() se borra y se reinserta en la MISMA
     * transaccion — sin esto el contexto de persistencia queda con una referencia stale
     * (mismo criterio que SpringDataRankingAprendizRepository, `points`). */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RecurrenciaJpaEntity r WHERE r.eventoId = :eventoId")
    void deleteByEventoId(@Param("eventoId") UUID eventoId);
}
