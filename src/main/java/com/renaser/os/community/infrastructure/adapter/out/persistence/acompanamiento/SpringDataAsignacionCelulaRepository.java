package com.renaser.os.community.infrastructure.adapter.out.persistence.acompanamiento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataAsignacionCelulaRepository extends JpaRepository<AsignacionCelulaJpaEntity, UUID> {

    List<AsignacionCelulaJpaEntity> findByUsuarioIdOrderByInicioDesc(UUID usuarioId);

    List<AsignacionCelulaJpaEntity> findByCelulaIdOrderByInicioDesc(UUID celulaId);

    Optional<AsignacionCelulaJpaEntity> findFirstByClaveOperacion(String claveOperacion);
}
