package com.renaser.os.academy.application.ports.out.asignacion;

import com.renaser.os.academy.domain.model.asignacion.AsignacionCurso;
import com.renaser.os.academy.domain.model.curso.CursoId;

import java.util.List;

public interface LoadAsignacionCursoPort {

    /** Todas las asignaciones (directas y por grupo, vigentes o no) de un curso — el caller filtra vigencia. */
    List<AsignacionCurso> porCurso(CursoId cursoId);
}
