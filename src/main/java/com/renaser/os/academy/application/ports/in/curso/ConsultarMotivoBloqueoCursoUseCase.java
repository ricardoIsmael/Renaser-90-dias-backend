package com.renaser.os.academy.application.ports.in.curso;

import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.shared.domain.UserId;

/**
 * GET /api/v1/cursos/{id}/preview — por que el actor no puede ver un curso.
 * SOLO revela el motivo cuando es por dia de programa (informacion inofensiva
 * de mostrar: "se desbloquea en tu dia X"). Cualquier otro motivo (rol,
 * no publicado, curso inexistente) devuelve {@link NoBloqueado} — mismo
 * criterio de no revelar de mas que `getCursoLockReason`
 * (RenaserBack `service.ts:176-223`).
 */
public interface ConsultarMotivoBloqueoCursoUseCase {

    MotivoBloqueoCurso motivo(UserId actorId, CursoId cursoId);

    sealed interface MotivoBloqueoCurso permits NoBloqueado, BloqueadoPorDia {
    }

    record NoBloqueado() implements MotivoBloqueoCurso {
    }

    record BloqueadoPorDia(String tituloBloqueado, int diaDesbloqueo, int programDayActual)
            implements MotivoBloqueoCurso {
    }
}
