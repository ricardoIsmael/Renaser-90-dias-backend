package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface SpringDataRecursoLeccionRepository extends JpaRepository<RecursoLeccionJpaEntity, Long> {

    List<RecursoLeccionJpaEntity> findByLeccionIdOrderByOrdenAsc(String leccionId);

    @Query("SELECT r.leccionId, COUNT(r) FROM RecursoLeccionJpaEntity r WHERE r.leccionId IN (:leccionIds) GROUP BY r.leccionId")
    List<Object[]> contarPorLecciones(@Param("leccionIds") List<String> leccionIds);
}
