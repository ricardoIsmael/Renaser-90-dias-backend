package com.renaser.os.academy.infrastructure.adapter.in.rest.curso;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMisCursosUseCase.CursoConProgreso;

/**
 * Item de GET /api/v1/cursos: los campos de {@link CursoResponse} MEZCLADOS
 * (no anidados) con `progreso` y `portada_firmada` — espejo del spread
 * `{...c, progreso, portada_firmada}` de `listarMisCursosAlumno`
 * (repository.ts:797-802 del repo viejo). `@JsonUnwrapped` reproduce ese
 * spread sin duplicar los 13 campos de `CursoResponse` a mano.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MiCursoResponse(@JsonUnwrapped CursoResponse curso, ProgresoCursoResponse progreso,
                               String portadaFirmada) {

    public static MiCursoResponse from(CursoConProgreso item) {
        return new MiCursoResponse(CursoResponse.from(item.curso()), ProgresoCursoResponse.from(item.progreso()),
                item.portadaFirmada());
    }
}
