package com.renaser.os.academy.infrastructure.adapter.in.rest.leccion;

import com.renaser.os.academy.domain.model.curso.RecursoLeccion;

/** Espejo de `LeccionRecurso` (`src/types/cursos.ts`). */
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
public record RecursoLeccionResponse(Long id, String leccionId, String nombre, String url, int orden) {

    public static RecursoLeccionResponse from(RecursoLeccion recurso) {
        return new RecursoLeccionResponse(recurso.id(), recurso.leccionId().value(), recurso.nombre(),
                recurso.url(), recurso.orden());
    }
}
