package com.renaser.os.calendar.application.ports.out.celula;

import com.renaser.os.shared.domain.UserId;

import java.util.UUID;

/**
 * Si una persona pertenece HOY a un grupo, con cualquier funcion: aprendiz (el grupo principal o uno
 * adicional, D-139), mentor (el de ese grupo, aunque lidere varios, D-141) o guia de la recepcion.
 *
 * <p><b>Por que existe (E-363, 2026-09-27).</b> El acceso a un evento de grupo comparaba el grupo del evento
 * con UN solo valor: {@code participantes_programa.celula_id}. Ese puntero nombra el grupo principal de un
 * aprendiz, y un mentor no lo tiene (no hace el programa). Resultado: el mentor del grupo recibia el
 * recordatorio pero el evento le daba 403 al abrirlo, no lo veia en su lista, y los integrantes adicionales
 * tampoco tenian acceso. La fuente de verdad de quien esta en cada grupo son las asignaciones vigentes
 * ({@code asignaciones_celula}), que son de {@code community}.
 */
@FunctionalInterface
public interface ConsultarPertenenciaAGrupoPort {

    boolean perteneceHoy(UUID celulaId, UserId usuarioId);
}
