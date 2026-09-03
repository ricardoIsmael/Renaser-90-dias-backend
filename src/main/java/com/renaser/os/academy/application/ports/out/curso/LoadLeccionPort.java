package com.renaser.os.academy.application.ports.out.curso;

import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.Leccion;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.academy.domain.model.curso.SeccionCursoId;

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

    /**
     * Identidad de TODAS las lecciones del catálogo (a qué curso y, si corresponde, a qué
     * sección pertenece cada una), en una sola consulta liviana — insumo de
     * {@code LeccionesVisiblesAcademyService} para calcular visibilidad en lote sin traer
     * el cuerpo completo ({@code cuerpoHtml}/{@code cuerpoMd}) de cada lección, que nadie
     * necesita para esa pregunta.
     */
    List<LeccionCatalogo> listarIdentificadores();

    record LeccionCatalogo(LeccionId id, CursoId cursoId, SeccionCursoId seccionId) {
    }
}
