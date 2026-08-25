package com.renaser.os.academy.application.ports.out.curso;

import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.Leccion;
import com.renaser.os.academy.domain.model.curso.LeccionId;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LoadLeccionPort {

    Optional<Leccion> byId(LeccionId id);

    /** Lecciones del curso, ordenadas por `orden` (mezcla sueltas y de seccion — el caller separa por `seccionId`). */
    List<Leccion> porCurso(CursoId cursoId);

    /**
     * Total de lecciones por curso, en todo el catalogo — insumo de
     * `total_lecciones` en el progreso (espejo de la agrupacion en JS que
     * hacia `listarMisCursosAlumno`, repository.ts:785-792, resuelto aca en
     * una sola query agregada).
     */
    Map<CursoId, Integer> contarTotalPorCurso();
}
