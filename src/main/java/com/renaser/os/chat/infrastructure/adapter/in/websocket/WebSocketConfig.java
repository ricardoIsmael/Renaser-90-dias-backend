package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Endpoint STOMP para el chat en vivo (CLAUDE.MD del encargo: reemplaza el polling).
 * El cliente se conecta a {@code /ws} y se suscribe a
 * {@code /topic/conversaciones/{conversacionId}} para recibir mensajes en tiempo real —
 * {@link com.renaser.os.chat.infrastructure.adapter.out.redis.RedisChatSubscriberConfig}
 * es quien empuja a ese topic. Broker simple en memoria (no STOMP broker relay a
 * RabbitMQ, CLAUDE.MD §5.2.1: con Redis ya en el stack, es el punto de partida por
 * defecto) — cada instancia solo entrega a SUS propios sockets, el fanout entre
 * instancias lo hace Redis Pub/Sub, no este broker.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ActorHandshakeInterceptor actorHandshakeInterceptor;
    private final SubscripcionAutorizadaInterceptor subscripcionAutorizadaInterceptor;
    /**
     * Los MISMOS origenes que CORS (auditoria NFR 2026-09-06; S-6 de la auditoria del
     * 2026-09-01). Antes era {@code setAllowedOriginPatterns("*")}: cualquier pagina web podia
     * abrir el socket contra produccion desde el navegador de un aprendiz logueado. La app
     * nativa no manda {@code Origin} y Spring la deja pasar igual; la lista solo restringe a los
     * navegadores, que es donde vive el riesgo.
     */
    private final List<String> origenesPermitidos;

    WebSocketConfig(ActorHandshakeInterceptor actorHandshakeInterceptor,
                     SubscripcionAutorizadaInterceptor subscripcionAutorizadaInterceptor,
                     @Value("${renaser.web.cors.origenes}") List<String> origenesPermitidos) {
        this.actorHandshakeInterceptor = actorHandshakeInterceptor;
        this.subscripcionAutorizadaInterceptor = subscripcionAutorizadaInterceptor;
        this.origenesPermitidos = List.copyOf(origenesPermitidos);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(origenesPermitidos.toArray(String[]::new))
                .addInterceptors(actorHandshakeInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(subscripcionAutorizadaInterceptor);
    }
}
