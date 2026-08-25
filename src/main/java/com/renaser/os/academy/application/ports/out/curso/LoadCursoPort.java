package com.renaser.os.academy.application.ports.out.curso;

import com.renaser.os.academy.domain.model.curso.Curso;
import com.renaser.os.academy.domain.model.curso.CursoId;

import java.util.List;
import java.util.Optional;

public interface LoadCursoPort {

    Optional<Curso> byId(CursoId id);

    /** Todo el catalogo (publicado o no), ordenado por `orden` — mismo criterio que `listarCursos`/`listarMisCursosAlumno`. */
    List<Curso> listarTodos();
}
