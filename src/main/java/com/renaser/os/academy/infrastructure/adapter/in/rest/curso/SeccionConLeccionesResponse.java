package com.renaser.os.academy.infrastructure.adapter.in.rest.curso;

import com.renaser.os.academy.application.ports.in.curso.ConsultarCursoDetalleUseCase.SeccionConLecciones;

import java.util.List;

/** Espejo de `SeccionConLecciones` (`src/types/cursos.ts`). */
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
public record SeccionConLeccionesResponse(String id, String cursoId, String titulo, int orden, Integer diaDesbloqueo,
                                           boolean bloqueadaPorDia, Integer programDayActual, int diasFaltantes,
                                           List<LeccionLiteResponse> lecciones) {

    public static SeccionConLeccionesResponse from(SeccionConLecciones item) {
        return new SeccionConLeccionesResponse(item.seccion().id().value(), item.seccion().cursoId().value(),
                item.seccion().titulo(), item.seccion().orden(), item.seccion().diaDesbloqueo(),
                item.bloqueadaPorDia(), item.programDayActual(), item.diasFaltantes(),
                item.lecciones().stream().map(LeccionLiteResponse::from).toList());
    }
}
