package com.renaser.os.academy.application.ports.out.curso;

import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.SeccionCurso;
import com.renaser.os.academy.domain.model.curso.SeccionCursoId;

import java.util.List;
import java.util.Optional;

public interface LoadSeccionCursoPort {

    Optional<SeccionCurso> byId(SeccionCursoId id);

    /** Secciones del curso, ordenadas por `orden`. */
    List<SeccionCurso> porCurso(CursoId cursoId);

    /**
     * TODAS las secciones del catálogo, de todos los cursos, en una sola consulta — insumo
     * de {@code LeccionesVisiblesAcademyService} para calcular la visibilidad de catálogo en
     * lote sin pedir secciones curso por curso (anti N+1, mismo criterio que
     * {@code ContarRegistrosDiariosHabitsPort}).
     */
    List<SeccionCurso> listarTodas();
}
