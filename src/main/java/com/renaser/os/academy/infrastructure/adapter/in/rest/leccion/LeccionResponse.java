package com.renaser.os.academy.infrastructure.adapter.in.rest.leccion;

import com.renaser.os.academy.domain.model.curso.Leccion;
import com.renaser.os.academy.domain.model.curso.TipoVideoLeccion;

import java.time.Instant;

/** Espejo EXACTO de `Leccion` (`src/types/cursos.ts`) — mismo criterio de wire que `CursoResponse` (ver su javadoc). */
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
public record LeccionResponse(String id, String cursoId, String seccionId, String titulo, int orden,
                               String cuerpoHtml, String cuerpoMd, String videoTipo, String videoUrl,
                               String videoMiniaturaUrl, Long videoDuracionMs, Instant creadoEn,
                               Instant actualizadoEn) {

    public static LeccionResponse from(Leccion leccion) {
        return new LeccionResponse(leccion.id().value(), leccion.cursoId().value(),
                leccion.seccionId() == null ? null : leccion.seccionId().value(), leccion.titulo(), leccion.orden(),
                leccion.cuerpoHtml(), leccion.cuerpoMd(), aWireVideoTipo(leccion.videoTipo()), leccion.videoUrl(),
                leccion.videoMiniaturaUrl(), leccion.videoDuracionMs(), leccion.creadoEn(), leccion.actualizadoEn());
    }

    private static String aWireVideoTipo(TipoVideoLeccion tipo) {
        if (tipo == null) {
            return null;
        }
        return switch (tipo) {
            case YOUTUBE -> "youtube";
            case STORAGE -> "storage";
        };
    }
}
