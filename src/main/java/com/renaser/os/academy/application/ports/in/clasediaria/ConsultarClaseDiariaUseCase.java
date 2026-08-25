package com.renaser.os.academy.application.ports.in.clasediaria;

import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.shared.domain.UserId;

/**
 * GET /api/v1/classroom/clase-diaria — resuelve la Clase Diaria para el dia
 * REAL del aprendiz (el servidor lee `dia_programa`, el movil nunca lo
 * manda). Espejo de {@code resolveAvailableClass}/{@code findClaseDiaria}
 * (RenaserBack `clase-diaria/service.ts` + `repository.ts`). Solo TRAINEE.
 *
 * <p>Este puerto es de SOLO LECTURA — resuelve que clase corresponde hoy.
 * Completarla (que ademas cierra el habito diario y otorga puntos) necesita
 * `habits`, que todavia no existe con ese hook: ver
 * `docs/MODULO_ACADEMY.md` §6 para el punto de coordinacion pendiente.
 */
public interface ConsultarClaseDiariaUseCase {

    ClaseDiariaResolution claseDeHoy(UserId actorId);

    sealed interface ClaseDiariaResolution permits Disponible, NoIniciado, Proximamente {
    }

    record Disponible(int programDay, CursoId cursoId, String cursoTitulo, LeccionId leccionId, String leccionTitulo)
            implements ClaseDiariaResolution {
    }

    /** `dia_programa == 0`: el aprendiz todavia no arranco el reloj de 90 dias. */
    record NoIniciado() implements ClaseDiariaResolution {
    }

    /** Dia con programa activo pero sin contenido de Clase Diaria resuelto para ese dia (fuera de rango 1-90, o hueco de catalogo). */
    record Proximamente(int programDay) implements ClaseDiariaResolution {
    }
}
