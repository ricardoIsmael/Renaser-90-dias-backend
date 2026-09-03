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
}
