package com.renaser.os.academy.application.ports.in.leccion;

import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.shared.domain.UserId;

/**
 * DELETE /api/v1/lecciones/{id}/complete — reemplaza la escritura directa que
 * la app hacia hoy contra `leccion_progreso` (`src/services/cursos.ts:
 * desmarcarLeccion` del repo RN, usado por la pantalla de leccion para
 * "descompletar" al alternar el check de una leccion ya vista,
 * `app/(app)/leccion/[id].tsx: alternarCompletada`). Inverso simetrico de
 * {@link CompletarLeccionUseCase} — misma exigencia de acceso vigente al
 * curso/seccion (AC-07, extendida a esta operacion por AC-16, ver
 * `docs/MODULO_ACADEMY.md` §5).
 *
 * <p>Idempotente: desmarcar una leccion que no estaba completada no falla —
 * mismo comportamiento "sin error" que el `.delete().eq(...)` de PostgREST
 * que este endpoint reemplaza (borrar cero filas no es un error).
 */
public interface DescompletarLeccionUseCase {

    void descompletar(UserId actorId, LeccionId leccionId);
}
