package com.renaser.os.academy.infrastructure.adapter.in.rest.curso;

import com.renaser.os.academy.application.ports.in.curso.ConsultarMisCursosUseCase.ProgresoCurso;

/** Espejo de `ProgresoCurso` (`src/types/cursos.ts`). `ultima_leccion_id` siempre null, ver el puerto de aplicacion. */
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
public record ProgresoCursoResponse(String cursoId, int totalLecciones, int completadas, String ultimaLeccionId) {

    public static ProgresoCursoResponse from(ProgresoCurso progreso) {
        return new ProgresoCursoResponse(progreso.cursoId().value(), progreso.totalLecciones(),
                progreso.completadas(), progreso.ultimaLeccionId() == null ? null : progreso.ultimaLeccionId().value());
    }
}
