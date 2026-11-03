package com.renaser.os.community.infrastructure.adapter.out.persistence.acompanamiento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataAsignacionCelulaRepository extends JpaRepository<AsignacionCelulaJpaEntity, UUID> {

    List<AsignacionCelulaJpaEntity> findByUsuarioIdOrderByInicioDesc(UUID usuarioId);

    List<AsignacionCelulaJpaEntity> findByCelulaIdOrderByInicioDesc(UUID celulaId);

    /** H-04c: las de varios grupos en una consulta, en el mismo orden que la de un grupo. */
    List<AsignacionCelulaJpaEntity> findByCelulaIdInOrderByInicioDesc(java.util.Collection<UUID> celulaIds);

    Optional<AsignacionCelulaJpaEntity> findFirstByClaveOperacion(String claveOperacion);
}
