package com.renaser.os.academy.infrastructure.adapter.in.rest.leccion;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.renaser.os.academy.domain.model.curso.Leccion;
import com.renaser.os.academy.domain.model.curso.TipoVideoLeccion;

import java.time.Instant;

/** Espejo EXACTO de `Leccion` (`src/types/cursos.ts`) — mismo criterio de wire que `CursoResponse` (ver su javadoc). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
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
