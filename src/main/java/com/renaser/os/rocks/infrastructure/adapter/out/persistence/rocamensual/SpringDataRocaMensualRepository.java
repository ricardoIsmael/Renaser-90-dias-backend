package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocamensual;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataRocaMensualRepository extends JpaRepository<RocaMensualJpaEntity, UUID> {

    /**
     * Los tramos mensuales de una persona. La tabla no guarda el participante —cuelga de la Roca
     * Maestra, ver V36— asi que hay que pasar por {@code rocas_maestras}. Es JPQL y no SQL nativo,
     * y las dos tablas son de este mismo modulo, asi que no cruza ninguna frontera de Modulith.
     *
     * <p>Ordenado por mes para que el cliente reciba el plan en el orden en que se lee, y no en el
     * que Postgres devuelva las filas.
     */
    @Query("""
            select m from RocaMensualJpaEntity m
            where m.rocaMaestraId in (
                select ma.id from RocaMaestraJpaEntity ma where ma.participanteId = :participanteId)
            order by m.numeroMes asc
            """)
    List<RocaMensualJpaEntity> deParticipante(@Param("participanteId") UUID participanteId);

    Optional<RocaMensualJpaEntity> findByRocaMaestraIdAndNumeroMes(UUID rocaMaestraId, Short numeroMes);
}
