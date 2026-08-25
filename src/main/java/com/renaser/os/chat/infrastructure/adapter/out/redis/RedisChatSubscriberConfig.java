package com.renaser.os.chat.infrastructure.adapter.out.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;

/**
 * El otro lado del fanout: cada instancia del backend se suscribe al mismo canal que
 * {@link RedisChatPublisher} publica, y reenvia lo que recibe a los sockets STOMP locales
 * conectados (CLAUDE.MD §5.2.1 — asi un usuario en la instancia 2 ve un mensaje escrito
 * por alguien conectado a la instancia 1). El payload NO se deserializa aca: viaja tal
 * cual (JSON) hasta el cliente STOMP, que lo interpreta.
 */
@Configuration
class RedisChatSubscriberConfig {

    private static final String PATRON_CANAL = "chat:conversacion:*";

    @Bean
    RedisMessageListenerContainer redisChatListenerContainer(RedisConnectionFactory connectionFactory,
                                                               SimpMessagingTemplate simpMessagingTemplate) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(reenviarAStomp(simpMessagingTemplate), new PatternTopic(PATRON_CANAL));
        return container;
    }

    private MessageListener reenviarAStomp(SimpMessagingTemplate simpMessagingTemplate) {
        return (message, pattern) -> {
            String canal = new String(message.getChannel(), StandardCharsets.UTF_8);
            String conversacionId = canal.substring(canal.lastIndexOf(':') + 1);
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            simpMessagingTemplate.convertAndSend("/topic/conversaciones/" + conversacionId, payload);
        };
    }
}
