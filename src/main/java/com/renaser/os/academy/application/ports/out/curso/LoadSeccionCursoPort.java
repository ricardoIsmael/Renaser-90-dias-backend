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
}
