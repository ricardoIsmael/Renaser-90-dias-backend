package com.renaser.os.rag.infrastructure.adapter.in.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.ServletWebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

/**
 * Registra el WebSocket crudo (no STOMP) de la voz en vivo (D-162), al lado del STOMP del chat
 * ({@code chat.WebSocketConfig}, {@code /ws}). Son dos mapeos independientes: este no toca el broker
 * ni los interceptores del chat.
 *
 * <p>Se registra siempre, aunque la voz en vivo este apagada: asi la app recibe un
 * {@code error} + cierre {@code no-disponible} claro, en vez de un 404 que no distingue "apagado" de
 * "backend viejo".
 *
 * <p>Mismos origenes que CORS y que el STOMP: la app nativa no manda {@code Origin}; la lista solo
 * restringe a los navegadores.
 */
@Configuration
@EnableWebSocket
class VozEnVivoWebSocketConfig implements WebSocketConfigurer {

    static final String RUTA = "/api/v1/renasia/voz/en-vivo";

    private final VozEnVivoWebSocketHandler handler;
    private final VozEnVivoHandshakeInterceptor interceptor;
    private final List<String> origenesPermitidos;

    VozEnVivoWebSocketConfig(VozEnVivoWebSocketHandler handler, VozEnVivoHandshakeInterceptor interceptor,
                             @Value("${renaser.web.cors.origenes}") List<String> origenesPermitidos) {
        this.handler = handler;
        this.interceptor = interceptor;
        this.origenesPermitidos = List.copyOf(origenesPermitidos);
    }

    /**
     * <b>Este mapeo va antes que los controllers</b> (E-236). Por defecto Spring MVC le da orden 1, y
     * los controllers van en 0: {@code GET /api/v1/renasia/voz/{id}} de {@code VozRenasiaController}
     * tomaba {@code en-vivo} como un {@code {id}}, no podia convertirlo a UUID y el handshake
     * terminaba en 400 sin llegar nunca al WebSocket. Este mapeo solo conoce su propia ruta, asi que
     * ir primero no le quita nada a nadie.
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        if (registry instanceof ServletWebSocketHandlerRegistry servlet) {
            servlet.setOrder(Ordered.HIGHEST_PRECEDENCE);
        }
        registry.addHandler(handler, RUTA)
                .addInterceptors(interceptor)
                .setAllowedOrigins(origenesPermitidos.toArray(String[]::new));
    }
}
