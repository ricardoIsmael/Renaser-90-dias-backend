package com.renaser.os.plataforma.infrastructure.adapter.in.redis;

import com.renaser.os.plataforma.application.services.CopiaDeDespliegues;
import com.renaser.os.plataforma.infrastructure.adapter.out.redis.CanalDeFuncionalidades;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * El aviso entre instancias para los despliegues (contrato fase 2 §3.2): escucha el mismo canal de los interruptores,
 * {@link CanalDeFuncionalidades#NOMBRE}, y recarga la copia de despliegues con CUALQUIER mensaje. No se filtra por
 * versión porque un despliegue terminado borra su fila (la versión «mayor» puede bajar) y porque recargar son dos
 * lecturas chicas tras un cambio hecho a mano.
 *
 * <p>Contenedor propio (no se suma al de {@link SuscripcionAlCanalDeFuncionalidades}) para no tocar la fase 1: es una
 * conexión de suscripción más por instancia.
 */
@Configuration
class SuscripcionDeDespliegues {

    @Bean
    RedisMessageListenerContainer redisDesplieguesListenerContainer(RedisConnectionFactory conexiones,
                                                                     CopiaDeDespliegues copia) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(conexiones);
        container.addMessageListener(oyentePara(copia), new ChannelTopic(CanalDeFuncionalidades.NOMBRE));
        return container;
    }

    /** El oyente de una copia; público para que una prueba arme una segunda instancia sobre la misma base. */
    public static MessageListener oyentePara(CopiaDeDespliegues copia) {
        return (mensaje, patron) -> copia.recargar();
    }
}
