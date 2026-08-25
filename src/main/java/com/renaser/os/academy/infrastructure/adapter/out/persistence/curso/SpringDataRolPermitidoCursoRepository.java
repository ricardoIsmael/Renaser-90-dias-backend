package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataRolPermitidoCursoRepository extends JpaRepository<RolPermitidoCursoJpaEntity, RolPermitidoCursoId> {

    List<RolPermitidoCursoJpaEntity> findByCursoId(String cursoId);

    List<RolPermitidoCursoJpaEntity> findByCursoIdIn(List<String> cursoIds);
}
