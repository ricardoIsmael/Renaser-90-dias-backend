package com.renaser.os.academy.infrastructure.adapter.in.rest.curso;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.renaser.os.academy.application.ports.in.curso.ConsultarCursoDetalleUseCase.SeccionConLecciones;

import java.util.List;

/** Espejo de `SeccionConLecciones` (`src/types/cursos.ts`). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
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
