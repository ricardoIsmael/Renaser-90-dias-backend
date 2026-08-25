package com.renaser.os.academy.infrastructure.adapter.in.rest.curso;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMisCursosUseCase.ProgresoCurso;

/** Espejo de `ProgresoCurso` (`src/types/cursos.ts`). `ultima_leccion_id` siempre null, ver el puerto de aplicacion. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProgresoCursoResponse(String cursoId, int totalLecciones, int completadas, String ultimaLeccionId) {

    public static ProgresoCursoResponse from(ProgresoCurso progreso) {
        return new ProgresoCursoResponse(progreso.cursoId().value(), progreso.totalLecciones(),
                progreso.completadas(), progreso.ultimaLeccionId() == null ? null : progreso.ultimaLeccionId().value());
    }
}
