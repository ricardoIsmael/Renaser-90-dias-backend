package com.renaser.os.rag.application.ports.out.conversacion;

import com.renaser.os.shared.domain.UserId;

import java.util.Set;

/**
 * Puerto propio de {@code rag} para saber qué lecciones puede ver HOY el actor que le
 * pregunta a Renasia. El gate de programa (rol + día de programa + publicación de curso y
 * sección) es una regla de {@code academy}, no de {@code rag} — por las reglas de Modulith
 * (CLAUDE.MD sec. 5.1), {@code rag} no puede importar {@code academy.domain.*} ni reimplementar
 * esa regla; el adaptador que implementa este puerto delega en
 * {@code academy.api.LeccionesVisiblesFinder}.
 *
 * <p>Deliberadamente NO expone ningún tipo de {@code academy}: el puerto nombra la intención
 * de negocio de este módulo ("qué lecciones son citables hoy para este actor"), no la forma
 * del contrato ajeno — mismo criterio que {@code LeerEntradasDiarioPort} con
 * {@code habits.api.EntradaDiarioSummary}.
 *
 * <p>Existe para resolver, ANTES de preguntarle a {@code VectorStorePort}, el conjunto que
 * {@code ConversacionRenasiaService} pasa como {@code VectorStorePort.FiltroLecciones}
 * (ver el javadoc de esa clase para la decisión de dónde vive el filtro).
 */
public interface ConsultarLeccionesVisiblesPort {

    /**
     * @return ids de lecciones que {@code actorId} puede ver hoy en el catálogo. Vacío si no
     * tiene ningún curso accesible — nunca {@code null}.
     */
    Set<String> visiblesParaActor(UserId actorId);
}
