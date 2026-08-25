package com.renaser.os.academy.infrastructure.adapter.in.rest.leccion;

import com.renaser.os.academy.application.ports.in.leccion.ConsultarLeccionUseCase.LeccionDetalle;

import java.util.List;

/** GET /api/v1/lecciones/{id} — espejo de `{leccion, recursos}` (`route.ts` viejo, no anida `recursos` dentro de `leccion`). */
public record LeccionDetalleResponse(LeccionResponse leccion, List<RecursoLeccionResponse> recursos) {

    public static LeccionDetalleResponse from(LeccionDetalle detalle) {
        return new LeccionDetalleResponse(LeccionResponse.from(detalle.leccion()),
                detalle.recursos().stream().map(RecursoLeccionResponse::from).toList());
    }
}
