package com.renaser.os.academy.application.ports.in.leccion;

import com.renaser.os.academy.domain.model.curso.Leccion;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.academy.domain.model.curso.RecursoLeccion;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * GET /api/v1/lecciones/{id} — entrega una leccion solo despues de validar
 * el acceso a su curso y el gate de dia de su seccion. Espejo de `getLeccion`
 * (RenaserBack `service.ts:130-164`).
 */
public interface ConsultarLeccionUseCase {

    LeccionDetalle leccion(UserId actorId, LeccionId leccionId);

    record LeccionDetalle(Leccion leccion, List<RecursoLeccion> recursos) {
    }
}
