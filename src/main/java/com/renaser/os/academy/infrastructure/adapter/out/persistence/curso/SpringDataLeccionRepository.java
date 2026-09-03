package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface SpringDataLeccionRepository extends JpaRepository<LeccionJpaEntity, String> {

    List<LeccionJpaEntity> findByCursoIdOrderByOrdenAsc(String cursoId);

    @Query("SELECT l.cursoId, COUNT(l) FROM LeccionJpaEntity l GROUP BY l.cursoId")
    List<Object[]> contarPorCurso();

    /**
     * Solo id/cursoId/seccionId — nunca {@code cuerpoHtml}/{@code cuerpoMd} (pueden ser
     * pesados y nadie los necesita para calcular visibilidad de catálogo en lote).
     */
    @Query("SELECT l.id, l.cursoId, l.seccionId FROM LeccionJpaEntity l")
    List<Object[]> listarIdentificadores();
}
