package com.renaser.os.notifications.application.ports.out.notificacion;

import com.renaser.os.notifications.domain.model.notificacion.Notificacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

public interface LoadNotificacionPort {

    /** Bandeja de un usuario: mas nueva primero, acotada por {@code desde} y {@code limite}
     * (ver {@code Notificacion.RETENCION_DIAS}/{@code LIMITE_BANDEJA}) — tope, no pagina. */
    List<Notificacion> bandeja(UserId usuarioId, Instant desde, int limite);

    /** Para distinguir "ya estaba leida" (200) de "no es tuya o no existe" (404) sin
     * exponer cual — mismo criterio que {@code notifications/repository.ts:existsForUser}. */
    boolean existeDe(Long id, UserId usuarioId);
}
