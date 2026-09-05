package com.renaser.os.academy.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Set;

/**
 * Contrato público de `academy`: qué lecciones puede ver HOY un actor en el catálogo.
 * Aplica la misma regla que ya usa {@code CatalogoAcademyService} para armar el árbol de
 * un curso — {@code Curso#visibleEnCatalogoPara} (rol + día de programa + publicación) y,
 * dentro de un curso visible, {@code SeccionCurso#visibleEnCatalogoPara} (el gate propio de
 * la sección, independiente del curso) — pero resuelta para TODO el catálogo de una sola vez,
 * no curso por curso ni lección por lección.
 *
 * <p>Primer consumidor: `rag`, que recupera fragmentos de la base de conocimiento por
 * similitud y necesita saber cuáles están permitidos citarle a ESTE actor hoy — sin este
 * finder, la búsqueda vectorial podía devolver contenido de una lección que el propio gate
 * de programa de `academy` todavía tiene bloqueada para esa persona (ver
 * {@code rag.infrastructure.adapter.out.academy}).
 */
public interface LeccionesVisiblesFinder {

    /**
     * @return ids de TODAS las lecciones visibles hoy para {@code actorId} (unión de todos
     * los cursos accesibles, sin importar a cuál pertenece cada una). Vacío si el actor no
     * existe, está suspendido, o no tiene ningún curso accesible — nunca {@code null}.
     */
    Set<String> leccionesVisiblesPara(UserId actorId);

    /**
     * D-102: la misma regla, acotada a UN curso. La necesita el tutor de cursos de `rag`
     * (Sparkie), que responde sobre el curso en que la persona esta parada y no debe citar
     * material de otros cursos.
     *
     * @return ids de las lecciones de {@code cursoId} visibles hoy para {@code actorId}. Vacío
     * si el curso no existe, esta bloqueado para el actor, o el actor no existe o esta
     * suspendido — nunca {@code null}.
     */
    Set<String> leccionesVisiblesPara(UserId actorId, String cursoId);
}
