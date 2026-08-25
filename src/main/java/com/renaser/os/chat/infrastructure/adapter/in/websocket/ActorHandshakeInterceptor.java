package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

/**
 * Captura {@code X-Actor-Id} del handshake HTTP (mismo header temporal que usa el resto
 * de la API, CLAUDE.MD §5.3.5) y lo deja en los atributos de la sesion WebSocket, para que
 * {@link SubscripcionAutorizadaInterceptor} pueda verificar pertenencia en cada SUBSCRIBE.
 * Sin header o con un UUID invalido, el handshake se rechaza (403) — antes de esto,
 * {@code /ws} aceptaba cualquier conexion sin identificar al actor.
 */
@Component
class ActorHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATRIBUTO_ACTOR_ID = "actorId";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String header = request.getHeaders().getFirst("X-Actor-Id");
        if (header == null || header.isBlank()) {
            response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
            return false;
        }
        try {
            attributes.put(ATRIBUTO_ACTOR_ID, UUID.fromString(header));
            return true;
        } catch (IllegalArgumentException e) {
            response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                Exception exception) {
        // nada que hacer
    }
}
