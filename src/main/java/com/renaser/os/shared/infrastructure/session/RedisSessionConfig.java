package com.renaser.os.shared.infrastructure.session;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson.SecurityJacksonModules;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configuracion segura de los atributos que Spring Session guarda en Redis.
 *
 * <p>El serializador por defecto de Spring Session es Java Serialization. La sesion contiene el
 * {@code SecurityContext}, por lo que aceptar bytes de Java Serialization desde Redis deja la
 * deserializacion atada a un formato ejecutable. JSON con los modulos de Spring Security conserva
 * el polimorfismo necesario para reconstruir la autenticacion y aplica el validador de tipos que
 * entrega el framework.
 *
 * <p>El nombre del bean es parte del contrato de Spring Session: solo
 * {@code springSessionDefaultRedisSerializer} reemplaza su serializador predeterminado.
 */
@Configuration(proxyBeanMethods = false)
class RedisSessionConfig implements BeanClassLoaderAware {

    private ClassLoader classLoader;

    @Bean(name = "springSessionDefaultRedisSerializer")
    RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        JsonMapper mapper = JsonMapper.builder()
                .addModules(SecurityJacksonModules.getModules(classLoader))
                .build();
        return new JacksonJsonRedisSerializer<>(mapper, Object.class);
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }
}
