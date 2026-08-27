package com.renaser.os.points.api;

import com.renaser.os.shared.domain.UserId;

/**
 * Contrato público de `points` para leer, desde `notifications`, la cantidad de
 * notificaciones sin leer de un participante (gap #21, {@code GET /home}). Vive en
 * `points.api` (no en `notifications.api`) por el mismo motivo que {@link PorcentajeRocasFinder}:
 * `notifications` ya depende transitivamente de `points` (escucha eventos de `rocks`, que
 * depende de `points`), así que `points` no puede depender de `notifications` en la otra
 * dirección sin crear un ciclo — DIP, `notifications.NotificacionesNoLeidasService`
 * implementa lo que este módulo declara.
 */
public interface NotificacionesNoLeidasFinder {

    /**
     * @param participanteId cuenta suspendida o inexistente: propaga {@code NotAuthorizedException}/
     *                        {@code NoSuchElementException} (mismo guard que {@code ListarNotificacionesUseCase}) —
     *                        el caller decide el HTTP, este puerto no lo sabe
     * @return cantidad de notificaciones sin leer dentro de la ventana de retención
     *         ({@code Notificacion.RETENCION_DIAS}, la misma que acota {@code bandeja}) — no
     *         está topada por {@code LIMITE_BANDEJA} (ese límite es de PAGINACIÓN de la
     *         bandeja, no tiene sentido aplicarlo a un COUNT)
     */
    long contarNoLeidas(UserId participanteId);
}
