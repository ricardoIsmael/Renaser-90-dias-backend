package com.renaser.os.academy.infrastructure.adapter.out.persistence.progreso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataProgresoLeccionRepository extends JpaRepository<ProgresoLeccionJpaEntity, ProgresoLeccionId> {

    List<ProgresoLeccionJpaEntity> findByUsuarioId(UUID usuarioId);
}
