package com.renaser.os.notifications.domain.model.notificacion;

/**
 * Espejo 1:1 del tipo Postgres {@code tipo_notificacion} (V1__baseline_renaser.sql:84).
 * Igual que en {@code support}/{@code phasecontracts}, el dominio de este modulo esta en
 * espanol (patron de los modulos nuevos, `users` es la excepcion historica en ingles,
 * ver docs/MODULO_SUPPORT.md §1.1).
 */
public enum TipoNotificacion {
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
