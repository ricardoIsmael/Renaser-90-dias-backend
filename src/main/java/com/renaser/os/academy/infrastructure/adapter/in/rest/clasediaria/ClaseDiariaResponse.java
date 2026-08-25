package com.renaser.os.academy.infrastructure.adapter.in.rest.clasediaria;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase.ClaseDiariaResolution;
import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase.Disponible;
import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase.NoIniciado;
import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase.Proximamente;

/**
 * GET /api/v1/classroom/clase-diaria — espejo de `ClaseDiariaResolution`
 * (RenaserBack `clase-diaria/service.ts:6-17`). CAMELCASE, sin `@JsonNaming`:
 * este DTO nunca fue una fila de tabla, siempre fue un estado calculado.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClaseDiariaResponse(String status, int programDay, String cursoId, String cursoTitulo,
                                   String leccionId, String leccionTitulo) {

    public static ClaseDiariaResponse from(ClaseDiariaResolution resolucion) {
        return switch (resolucion) {
            case Disponible d -> new ClaseDiariaResponse("available", d.programDay(), d.cursoId().value(),
                    d.cursoTitulo(), d.leccionId().value(), d.leccionTitulo());
            case NoIniciado ignored -> new ClaseDiariaResponse("not_started", 0, null, null, null, null);
            case Proximamente p -> new ClaseDiariaResponse("coming_soon", p.programDay(), null, null, null, null);
        };
    }
}
