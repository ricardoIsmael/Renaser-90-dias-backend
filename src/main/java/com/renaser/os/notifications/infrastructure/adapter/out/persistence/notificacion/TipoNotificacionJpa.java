package com.renaser.os.notifications.infrastructure.adapter.out.persistence.notificacion;

/** Espejo de {@code com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion} —
 * tipo propio para que la entidad JPA nunca importe un tipo de dominio (CLAUDE.MD §5.4.5). */
public enum TipoNotificacionJpa {
    RECORDATORIO_HABITO,
    RECORDATORIO_ROCA,
    RECORDATORIO_RADAR,
    MENSAJE_MENTOR,
    ANUNCIO_SISTEMA,
    RESUMEN_SEMANAL,
    LOGRO_DESBLOQUEADO,
    HITO_PROGRAMA,
    MENSAJE_CHAT,
    TICKET_RESPONDIDO,
    TICKET_ABIERTO,
    SANTUARIO_ROTO,
    HABITO_PERSONAL_MODIFICADO
}
