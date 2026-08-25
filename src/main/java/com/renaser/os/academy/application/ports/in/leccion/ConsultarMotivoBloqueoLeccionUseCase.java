package com.renaser.os.academy.application.ports.in.leccion;

import com.renaser.os.academy.application.ports.in.curso.ConsultarMotivoBloqueoCursoUseCase.MotivoBloqueoCurso;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.shared.domain.UserId;

/**
 * GET /api/v1/lecciones/{id}/preview — mismo motivo que
 * {@code ConsultarMotivoBloqueoCursoUseCase}, partiendo de una leccion: la
 * leccion hereda el bloqueo por dia de su curso/seccion. Espejo de
 * `getLeccionLockReason` (RenaserBack `service.ts:228-264`).
 */
public interface ConsultarMotivoBloqueoLeccionUseCase {

    MotivoBloqueoCurso motivo(UserId actorId, LeccionId leccionId);
}
