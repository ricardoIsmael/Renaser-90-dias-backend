package com.renaser.os.evidence.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code JpaSpecificationExecutor} habilita {@code buscar} (hueco #19/#20): hasta 5
 * filtros opcionales (participante, estado, tipo de destino, desde, hasta) más el cursor
 * de keyset — con el patrón de un método JPQL por combinación que usa
 * {@code community.SpringDataPublicacionRepository} (E-31,
 * {@code docs/BITACORA_ERRORES.md}) serían 2^6 métodos. Con {@code Specification} cada
 * predicado se agrega SOLO si el filtro tiene valor — nunca se genera un
 * {@code :param IS NULL}, así que E-31 no puede repetirse acá por construcción, sin
 * necesidad de partir en métodos. Ver {@link EvidenciaSpecifications}.
 */
interface SpringDataEvidenciaRepository extends JpaRepository<EvidenciaJpaEntity, UUID>,
        JpaSpecificationExecutor<EvidenciaJpaEntity> {

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
