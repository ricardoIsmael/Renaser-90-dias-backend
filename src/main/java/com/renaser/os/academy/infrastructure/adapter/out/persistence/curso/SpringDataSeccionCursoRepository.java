package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataSeccionCursoRepository extends JpaRepository<SeccionCursoJpaEntity, String> {

    List<SeccionCursoJpaEntity> findByCursoIdOrderByOrdenAsc(String cursoId);
}
