package com.renaser.os.academy.infrastructure.adapter.out.persistence.asignacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataAsignacionCursoRepository extends JpaRepository<AsignacionCursoJpaEntity, Long> {

    List<AsignacionCursoJpaEntity> findByCursoId(String cursoId);
}
