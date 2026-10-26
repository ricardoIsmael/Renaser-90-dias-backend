package com.renaser.os.plataforma.infrastructure.adapter.in.redis;

import com.renaser.os.plataforma.application.services.CopiaDeRolesPersonalizados;
import com.renaser.os.plataforma.infrastructure.adapter.out.redis.CanalDeRolesPersonalizados;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * El otro lado del aviso de roles personalizados entre instancias (D-272): cada instancia escucha
 * {@link CanalDeRolesPersonalizados#NOMBRE} y recarga su copia. También recibe su propio aviso: recargar dos veces es
 * inofensivo (dos consultas chicas).
 */
@Configuration
public class SuscripcionAlCanalDeRolesPersonalizados {

    @Bean
    RedisMessageListenerContainer redisRolesPersonalizadosListenerContainer(RedisConnectionFactory conexiones,
                                                                            CopiaDeRolesPersonalizados copia) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(conexiones);
        container.addMessageListener(oyentePara(copia), new ChannelTopic(CanalDeRolesPersonalizados.NOMBRE));
        return container;
    }

    /** El oyente de una copia; público para que una prueba arme una segunda instancia. */
    public static MessageListener oyentePara(CopiaDeRolesPersonalizados copia) {
        return (mensaje, patron) -> copia.recargar();
    }
}
