package com.renaser.os.academy.infrastructure.adapter.in.rest.curso;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMotivoBloqueoCursoUseCase.BloqueadoPorDia;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMotivoBloqueoCursoUseCase.MotivoBloqueoCurso;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMotivoBloqueoCursoUseCase.NoBloqueado;

/**
 * GET /api/v1/cursos/{id}/preview y GET /api/v1/lecciones/{id}/preview —
 * espejo de `MotivoBloqueoCurso` (`src/types/cursos.ts`). CAMELCASE (asi lo
 * devolvia el endpoint viejo) y los campos ausentes se omiten
 * (`{locked:false}` sin mas claves) — `@JsonInclude(NON_NULL)`.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MotivoBloqueoResponse(boolean locked, String reason, String cursoTitulo, Integer diaDesbloqueo,
                                     Integer programDayActual) {

    public static MotivoBloqueoResponse from(MotivoBloqueoCurso motivo) {
        return switch (motivo) {
            case NoBloqueado ignored -> new MotivoBloqueoResponse(false, null, null, null, null);
            case BloqueadoPorDia b -> new MotivoBloqueoResponse(true, "dia_desbloqueo", b.tituloBloqueado(),
                    b.diaDesbloqueo(), b.programDayActual());
        };
    }
}
