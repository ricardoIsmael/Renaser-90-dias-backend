package com.renaser.os.rag.infrastructure.adapter.in.redis;

import com.renaser.os.rag.application.services.circuito.CopiaDelCircuito;
import com.renaser.os.rag.infrastructure.adapter.out.redis.CanalDelCircuito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * El otro lado del aviso entre instancias (plan.md D7): cada instancia escucha {@link CanalDelCircuito#NOMBRE} y relee
 * su copia del circuito. Sin esto, otra instancia seguiría llamando al proveedor hasta su recarga periódica.
 */
@Configuration
class SuscripcionAlCanalDelCircuito {

    @Bean
    RedisMessageListenerContainer redisCircuitoIaListenerContainer(RedisConnectionFactory conexiones,
                                                                   CopiaDelCircuito copia) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(conexiones);
        container.addMessageListener(oyentePara(copia), new ChannelTopic(CanalDelCircuito.NOMBRE));
        return container;
    }

    /** Relee todo, sea cual sea la parte que llegó: son dos lecturas chicas. */
    static MessageListener oyentePara(CopiaDelCircuito copia) {
        return (mensaje, patron) -> copia.recargar();
    }
}
