package com.renaser.os.academy.application.ports.in.leccion;

import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.academy.domain.model.progreso.ProgresoLeccion;
import com.renaser.os.shared.domain.UserId;

/**
 * POST /api/v1/lecciones/{id}/complete — reemplaza la escritura directa que
 * la app hacia hoy contra `leccion_progreso` (RLS de Supabase,
 * `src/services/cursos.ts: marcarLeccionCompletada` del repo RN). Cambio de
 * release COORDINADO: hasta que la app apunte a este endpoint, sigue
 * escribiendo directo — ver `docs/MODULO_ACADEMY.md` §6.
 *
 * <p>Idempotente: completar la misma leccion dos veces no falla ni duplica
 * fila (mismo criterio que `markLeccionCompleted`, `clase-diaria/repository.ts:236`).
 * A diferencia del repo viejo, exige acceso vigente al curso — no se puede
 * marcar completada una leccion que el actor no puede ver.
 */
public interface CompletarLeccionUseCase {

    ProgresoLeccion completar(UserId actorId, LeccionId leccionId);
}
