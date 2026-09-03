package com.renaser.os.academy.infrastructure.adapter.in.rest.curso;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMisCursosUseCase.CursoConProgreso;

/**
 * Item de GET /api/v1/cursos: los campos de {@link CursoResponse} MEZCLADOS
 * (no anidados) con `progreso` y `portada_firmada` — espejo del spread
 * `{...c, progreso, portada_firmada}` de `listarMisCursosAlumno`
 * (repository.ts:797-802 del repo viejo). `@JsonUnwrapped` reproduce ese
 * spread sin duplicar los 13 campos de `CursoResponse` a mano.
 */
/*
 * SIN @JsonNaming a proposito (2026-09-01). Estos DTOs declaraban
 * `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` importado de
 * `com.fasterxml.jackson.databind.annotation` — o sea de JACKSON 2. Spring Boot 4 serializa con
 * JACKSON 3, que vive en `tools.jackson.*`, y esa anotacion la ignora en silencio: no falla, no
 * avisa, simplemente no la aplica. Resultado: los 10 DTOs de academy declaraban snake_case y
 * mandaban camelCase, y el frontend que confio en la anotacion no pudo leer ni un curso.
 * Se quitan en vez de corregir el import porque el resto de la API ya es camelCase: dejar
 * academy en snake_case lo volveria la unica excepcion. Ver E-65 en docs/BITACORA_ERRORES.md.
 */
public record MiCursoResponse(@JsonUnwrapped CursoResponse curso, ProgresoCursoResponse progreso,
                               String portadaFirmada) {

    public static MiCursoResponse from(CursoConProgreso item) {
        return new MiCursoResponse(CursoResponse.from(item.curso()), ProgresoCursoResponse.from(item.progreso()),
                item.portadaFirmada());
    }
}
