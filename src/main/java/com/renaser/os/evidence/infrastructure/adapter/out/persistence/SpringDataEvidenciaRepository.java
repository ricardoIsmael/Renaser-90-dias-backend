package com.renaser.os.evidence.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataEvidenciaRepository extends JpaRepository<EvidenciaJpaEntity, UUID> {

    /**
     * {@code FOR UPDATE SKIP LOCKED} sobre {@code evidencias_cola_ia_idx} — mismo idiom
     * que {@code calendar.SpringDataRecordatorioRepository.vencidosPendientes}
     * (PESSIMISTIC_WRITE + hint de lock timeout -2, que Hibernate traduce a SKIP LOCKED
     * en el dialecto Postgres). Seguro con múltiples instancias del scheduler corriendo
     * a la vez: cada una se lleva un lote disjunto de la cola de validación IA.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT e FROM EvidenciaJpaEntity e WHERE e.estadoValidacion = :estado AND e.subidaEn <= :hasta "
            + "ORDER BY e.subidaEn ASC")
    List<EvidenciaJpaEntity> pendientesLote(@Param("estado") EstadoValidacionJpa estado, @Param("hasta") Instant hasta,
                                             Pageable pageable);
}
