package com.renaser.os.academy.infrastructure.adapter.in.rest.leccion;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.renaser.os.academy.domain.model.curso.RecursoLeccion;

/** Espejo de `LeccionRecurso` (`src/types/cursos.ts`). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RecursoLeccionResponse(Long id, String leccionId, String nombre, String url, int orden) {

    public static RecursoLeccionResponse from(RecursoLeccion recurso) {
        return new RecursoLeccionResponse(recurso.id(), recurso.leccionId().value(), recurso.nombre(),
                recurso.url(), recurso.orden());
    }
}
