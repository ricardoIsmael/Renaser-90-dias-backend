package com.renaser.os.chat.infrastructure.adapter.in.websocket;

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

    WebSocketConfig(ActorHandshakeInterceptor actorHandshakeInterceptor,
                     SubscripcionAutorizadaInterceptor subscripcionAutorizadaInterceptor) {
        this.actorHandshakeInterceptor = actorHandshakeInterceptor;
        this.subscripcionAutorizadaInterceptor = subscripcionAutorizadaInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").addInterceptors(actorHandshakeInterceptor);
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
