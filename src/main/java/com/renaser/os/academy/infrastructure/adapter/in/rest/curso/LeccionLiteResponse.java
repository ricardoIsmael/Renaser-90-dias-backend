package com.renaser.os.academy.infrastructure.adapter.in.rest.curso;

import com.renaser.os.academy.application.ports.in.curso.ConsultarCursoDetalleUseCase.LeccionConProgresion;
import com.renaser.os.academy.domain.model.curso.Leccion;
import com.renaser.os.academy.domain.model.curso.TipoVideoLeccion;

/** Espejo de `LeccionLite`/`LeccionResumenConProgresion` (`src/types/cursos.ts`) — sin cuerpo, para el arbol del curso. */
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
public record LeccionLiteResponse(String id, String cursoId, String seccionId, String titulo, int orden,
                                   String videoTipo, Long videoDuracionMs, boolean tieneCuerpo, int recursosCount,
                                   Integer diaDesbloqueo, boolean bloqueadaPorDia, Integer programDayActual,
                                   int diasFaltantes) {

    public static LeccionLiteResponse from(LeccionConProgresion item) {
        Leccion l = item.leccion();
        return new LeccionLiteResponse(l.id().value(), l.cursoId().value(),
                l.seccionId() == null ? null : l.seccionId().value(), l.titulo(), l.orden(),
                aWireVideoTipo(l.videoTipo()), l.videoDuracionMs(), l.tieneCuerpo(), item.recursosCount(),
                item.diaDesbloqueo(), item.bloqueadaPorDia(), item.programDayActual(), item.diasFaltantes());
    }

    static String aWireVideoTipo(TipoVideoLeccion tipo) {
        if (tipo == null) {
            return null;
        }
        return switch (tipo) {
            case YOUTUBE -> "youtube";
            case STORAGE -> "storage";
        };
    }
}
