package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface SpringDataLeccionRepository extends JpaRepository<LeccionJpaEntity, String> {

    List<LeccionJpaEntity> findByCursoIdOrderByOrdenAsc(String cursoId);

    @Query("SELECT l.cursoId, COUNT(l) FROM LeccionJpaEntity l GROUP BY l.cursoId")
    List<Object[]> contarPorCurso();
}
