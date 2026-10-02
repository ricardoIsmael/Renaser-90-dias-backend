package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.application.ports.in.conversacion.ListarSoportesUseCase.PaginaDeSoportes;

import java.util.List;

/**
 * Una página de la sección «Soporte» (D-249). Cada fila es exactamente la de {@code GET /chat/conversations}
 * ({@link ConversacionResumenResponse}), para que la app la pinte y la abra con el mismo código.
 *
 * @param nextCursor          para pedir la siguiente; {@code null} si no hay más
 * @param totalCount          cuántos soportes coinciden en total; solo en la primera página
 * @param unreadConversations en cuántos de ellos hay mensajes sin leer; solo en la primera página
 */
public record SoportesPageResponse(List<ConversacionResumenResponse> conversations, String nextCursor,
                                   boolean hasMore, Long totalCount, Long unreadConversations) {

    public static SoportesPageResponse from(PaginaDeSoportes pagina) {
        return new SoportesPageResponse(
                pagina.conversaciones().stream().map(ConversacionResumenResponse::from).toList(),
                pagina.siguiente() != null ? pagina.siguiente().escribir() : null,
                pagina.siguiente() != null, pagina.total(), pagina.conNoLeidos());
    }
}
