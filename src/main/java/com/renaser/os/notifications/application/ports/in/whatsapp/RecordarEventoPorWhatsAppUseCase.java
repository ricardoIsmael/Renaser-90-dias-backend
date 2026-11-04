package com.renaser.os.notifications.application.ports.in.whatsapp;

import com.renaser.os.notifications.application.ports.in.evento.AvisoDeMensajeDelEvento;

/**
 * Manda el recordatorio de un evento por WhatsApp (spec 041, FR-010 a FR-025). Es un canal extra: <b>nunca lanza por una falla del proveedor</b>
 * (FR-021: el *outbox* reintentaria y mandaria dos veces); solo lanza si no pudo ni apartar la entrega, y entonces nada salio y el reintento es seguro.
 */
public interface RecordarEventoPorWhatsAppUseCase {

    void recordar(AvisoDeMensajeDelEvento aviso);
}
