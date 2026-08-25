package com.renaser.os.academy.infrastructure.adapter.in.rest.curso;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.renaser.os.academy.application.ports.in.curso.ConsultarCursoDetalleUseCase.LeccionConProgresion;
import com.renaser.os.academy.domain.model.curso.Leccion;
import com.renaser.os.academy.domain.model.curso.TipoVideoLeccion;

/** Espejo de `LeccionLite`/`LeccionResumenConProgresion` (`src/types/cursos.ts`) — sin cuerpo, para el arbol del curso. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
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
