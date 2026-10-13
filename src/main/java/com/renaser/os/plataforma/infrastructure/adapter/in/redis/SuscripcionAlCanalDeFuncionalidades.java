package com.renaser.os.plataforma.infrastructure.adapter.in.redis;

import com.renaser.os.plataforma.application.services.CopiaDeEstados;
import com.renaser.os.plataforma.infrastructure.adapter.out.redis.CanalDeFuncionalidades;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * El otro lado del aviso entre instancias (plan.md D3): cada instancia escucha {@link CanalDeFuncionalidades#NOMBRE} y,
 * si la versión que llega es más nueva que la de su copia, la recarga. Un mensaje que no es un número se descarta.
 */
@Configuration
class SuscripcionAlCanalDeFuncionalidades {

    private static final Logger log = LoggerFactory.getLogger(SuscripcionAlCanalDeFuncionalidades.class);

    @Bean
    RedisMessageListenerContainer redisFuncionalidadesListenerContainer(RedisConnectionFactory conexiones,
                                                                         CopiaDeEstados copia) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(conexiones);
        container.addMessageListener(oyentePara(copia), new ChannelTopic(CanalDeFuncionalidades.NOMBRE));
        return container;
    }

    /** El oyente de una copia; público para que una prueba arme una segunda instancia sobre la misma base. */
    public static MessageListener oyentePara(CopiaDeEstados copia) {
        return (mensaje, patron) -> {
            String cuerpo = new String(mensaje.getBody(), StandardCharsets.UTF_8).strip();
            try {
                copia.alAvisoDeVersion(Long.parseLong(cuerpo));
            } catch (NumberFormatException e) {
                log.warn("[plataforma] aviso de funcionalidades descartado: «{}» no es una version", cuerpo);
            }
        };
    }
}
