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
    HABITO_PERSONAL_MODIFICADO,
    /** V46: el mentor debería mirar a un aprendiz suyo (ausencia o evidencia vencida). */
    ACOMPANAMIENTO_ALUMNO,

    /** A un grupo programado se le acaba el periodo y el administrador tiene que reprogramarlo. */
    GRUPO_POR_VENCER,

    /**
     * V59: un aprendiz repitio, en pocos dias, expresiones de malestar al escribirle al asistente.
     * <b>No es un diagnostico</b> — es un patron de texto que se repitio y conviene mirar. Le llega
     * a ADMIN y ALCHEMIST, y nunca lleva una sola palabra de lo que la persona escribio.
     */
    PATRON_DE_MALESTAR_REPETIDO,

    /**
     * V70 (D-183): un recordatorio de la cola {@code recordatorios_evento} de `calendar` — "tu evento
     * empieza en 10 min", la alarma de 04:50 de la Semana de Manifestacion, o el anuncio de un evento
     * nuevo. Tipo propio y no {@code ANUNCIO_SISTEMA} para que se pueda silenciar por separado.
     */
    RECORDATORIO_EVENTO,

    /**
     * V87 (D-240): falta armar algo para que la gente tenga grupo — no hay ningún grupo en curso al
     * que trasladar a quien terminó la bienvenida (al administrador y al líder), o un grupo en curso
     * no tiene mentor (al líder).
     */
    ARMADO_DE_GRUPOS;

    /**
     * Si el aviso se ve en la campana de la app (D-221). Los mensajes de chat no: el chat tiene sus
     * propios no leídos, como WhatsApp, y la comunidad llenaría la campana con cada mensaje. Su fila se
     * guarda igual (deduplica el push ante un reintento) y se purga antes
     * ({@code Notificacion.RETENCION_MENSAJES_CHAT_DIAS}).
     */
    public boolean seVeEnLaCampana() {
        return this != MENSAJE_CHAT;
    }
}
