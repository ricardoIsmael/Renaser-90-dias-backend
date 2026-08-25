package com.renaser.os.calendar.application.ports.out.curso;

import com.renaser.os.shared.domain.UserId;

import java.util.Set;

/**
 * Puerto OUT propio de `calendar` para la audiencia CURSO. El adaptador de infraestructura
 * delega en {@code academy.api.AccesoCursoFinder} (ya publicado por `academy` para este uso
 * exacto) — este puerto existe para que el servicio de `calendar` no dependa directo de un
 * tipo de otro modulo y sus tests unitarios puedan mockear esto sin levantar `academy`.
 */
public interface ResolverAudienciaCursoPort {

    boolean tieneAcceso(UserId usuarioId, String cursoId);

    /** Restringido a los candidatos que ya pasaron el filtro de audiencia base (trainees
     * activos) — mismo criterio que filterUsersWithCourseAccess() del repo viejo. */
    Set<UserId> filtrarConAcceso(String cursoId, Set<UserId> candidatos);
}
