package com.renaser.os.academy.application.ports.in.curso;

import com.renaser.os.academy.domain.model.curso.Curso;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/** GET /api/v1/cursos — catalogo accesible del actor, con su progreso. Espejo de `getMisCursos`. */
public interface ConsultarMisCursosUseCase {

    List<CursoConProgreso> misCursos(UserId actorId);

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
