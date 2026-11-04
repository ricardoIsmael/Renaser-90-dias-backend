package com.renaser.os.notifications.application.ports.in.evento;

/**
 * Escribe el recordatorio de un evento en el chat de soporte de la persona (spec 041, FR-001 a FR-006). Decide A QUIEN (que sea del rol y de la audiencia
 * que corresponden, que no haya apagado los recordatorios de eventos) y CUANDO sirve; QUE decir y DONDE escribirlo lo resuelve {@code chat}.
 */
public interface RecordarEventoEnElChatUseCase {

    void recordar(AvisoDeMensajeDelEvento aviso);
}
