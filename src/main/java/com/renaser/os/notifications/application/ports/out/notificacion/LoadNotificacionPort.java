package com.renaser.os.notifications.application.ports.out.notificacion;

import com.renaser.os.notifications.domain.model.notificacion.Notificacion;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

public interface LoadNotificacionPort {

    /** Bandeja de un usuario: mas nueva primero, acotada por {@code desde} y {@code limite}
     * (ver {@code Notificacion.RETENCION_DIAS}/{@code LIMITE_BANDEJA}) — tope, no pagina. */
    List<Notificacion> bandeja(UserId usuarioId, Instant desde, int limite);

    /** Para distinguir "ya estaba leida" (200) de "no es tuya o no existe" (404) sin
     * exponer cual — mismo criterio que {@code notifications/repository.ts:existsForUser}. */
    boolean existeDe(Long id, UserId usuarioId);

    /** COUNT dedicado (no "cargar bandeja y contar"): lo consume {@code notifications.api
     * .NotificacionesNoLeidasFinder} para el agregador de Home, que no necesita las filas. */
    long contarNoLeidas(UserId usuarioId, Instant desde);

    /**
     * ¿Ya existe una notificación para ese {@code (usuario, tipo, origenEventoId)}?
     *
     * <p>Es la MISMA tripleta del índice único {@code notificaciones_origen_evento_uk} (C-7/V16),
     * y existe para poder preguntar ANTES de insertar en vez de enterarse por la excepción.
     *
     * <p><b>No reemplaza al índice ni al {@code catch}</b>: entre esta consulta y el INSERT cabe
     * otra transacción. El índice sigue siendo la garantía; esto solo evita el camino ruidoso en
     * el caso normal, que es el 99 % de las veces.
     */
    boolean existePorOrigen(UserId usuarioId, TipoNotificacion tipo, UUID origenEventoId);
}
