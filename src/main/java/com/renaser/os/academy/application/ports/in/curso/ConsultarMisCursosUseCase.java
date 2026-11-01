package com.renaser.os.academy.application.ports.in.curso;

import com.renaser.os.academy.domain.model.curso.Curso;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/** GET /api/v1/cursos — catalogo accesible del actor, con su progreso. Espejo de `getMisCursos`. */
public interface ConsultarMisCursosUseCase {

    default List<CursoConProgreso> misCursos(UserId actorId) {
        return misCursos(actorId, null);
    }

    /**
     * Spec 024: el catálogo es el de la formación de quien pide. Solo Administración, Alquimista y Soporte TI eligen una; sin elegir ven los de las
     * tres, cada curso con la suya.
     */
    List<CursoConProgreso> misCursos(UserId actorId, Formacion formacion);

    record CursoConProgreso(Curso curso, ProgresoCurso progreso, String portadaFirmada) {
    }

    /**
     * {@code ultimaLeccionId} nunca se completa hoy — el repo viejo tampoco
     * lo hacia ({@code ultima_leccion_id: null} fijo en
     * `listarMisCursosAlumno`, repository.ts:797). Se conserva el campo por
     * fidelidad de wire, no por una funcionalidad real.
     */
    record ProgresoCurso(CursoId cursoId, int totalLecciones, int completadas, LeccionId ultimaLeccionId) {
    }
}
