package com.renaser.os.notifications.infrastructure.adapter.in.rest.notificacion;

import com.renaser.os.notifications.domain.model.notificacion.Notificacion;

import java.time.Instant;

/**
 * Proyeccion explicita de salida (CLAUDE.MD §5.4.1/§8) — nombres de campo en INGLES a
 * proposito, para preservar el contrato JSON del repo viejo
 * (`notifications/schema.ts:NotificationItem`: "Renombrar un campo rompe el badge de no
 * leidos"). Dos rupturas de contrato SI heredadas y documentadas (no inventadas por este
 * modulo, mismo criterio que `docs/MODULO_PHASECONTRACTS.md` §4):
 * <ul>
 *   <li>{@code id} pasa de string a numero (bigint IDENTITY en vez del cuid de Prisma).</li>
 *   <li>{@code type} viaja en espanol ({@code TipoNotificacion} de la BD nueva), el repo
 *   viejo lo devolvia en ingles.</li>
 * </ul>
 */
public record NotificacionResponse(Long id, String type, String title, String body, Instant createdAt,
                                    Instant readAt, String route) {

    public static NotificacionResponse from(Notificacion n) {
        return new NotificacionResponse(n.id(), n.tipo().name(), n.titulo(), n.cuerpo(), n.creadoEn(), n.leidaEn(),
                n.rutaApp());
    }
}
