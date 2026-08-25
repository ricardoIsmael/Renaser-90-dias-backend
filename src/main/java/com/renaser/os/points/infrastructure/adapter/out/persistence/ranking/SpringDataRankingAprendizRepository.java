package com.renaser.os.points.infrastructure.adapter.out.persistence.ranking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

interface SpringDataRankingAprendizRepository extends JpaRepository<RankingAprendizJpaEntity, RankingAprendizId> {

    /** Consulta JPQL tipada: el enum se compara vía el tipo mapeado por Hibernate
     * (@JdbcTypeCode NAMED_ENUM), sin el problema de cast que tendría un bind param
     * crudo contra una columna enum nativa de Postgres en una consulta nativa. */
    List<RankingAprendizJpaEntity> findByTipoAndFechaOrderByPosicion(TipoRankingJpa tipo, LocalDate fecha);

    /**
     * clearAutomatically=true es necesario: sin esto, un DELETE en bloque (bulk JPQL) no
     * sincroniza el contexto de persistencia (primer nivel de cache de Hibernate) — si el
     * mismo tipo+fecha se reemplaza dos veces en la MISMA transaccion (ver el test de
     * idempotencia de RankingPersistenceAdapterTest), el `saveAll` posterior encontraria
     * las entidades "viejas" todavia MANAGED en cache y generaria un UPDATE contra filas
     * que el DELETE ya borro (0 filas afectadas, sin error, pero sin insertar nada). Limpiar
     * el contexto fuerza que el siguiente save() trate la fila como nueva otra vez.
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from RankingAprendizJpaEntity r where r.tipo = :tipo and r.fecha = :fecha")
    void deleteByTipoAndFecha(TipoRankingJpa tipo, LocalDate fecha);
}
