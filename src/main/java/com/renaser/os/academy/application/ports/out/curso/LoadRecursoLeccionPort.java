package com.renaser.os.academy.application.ports.out.curso;

import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.academy.domain.model.curso.RecursoLeccion;

import java.util.List;
import java.util.Map;

public interface LoadRecursoLeccionPort {

    /** Recursos de una leccion, ordenados por `orden`. */
    List<RecursoLeccion> porLeccion(LeccionId leccionId);

    /** Cantidad de recursos por leccion, para pintar el arbol del curso sin traer los recursos completos. */
    Map<LeccionId, Integer> contarPorLecciones(List<LeccionId> leccionIds);
}
