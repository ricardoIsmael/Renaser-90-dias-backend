package com.renaser.os.shared.infrastructure.session;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson.SecurityJacksonModules;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;
import java.time.Instant;

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
 *
 * <h2>Por que la lista blanca no puede ser solo la de Spring Security</h2>
 *
 * La primera version llamaba a {@code SecurityJacksonModules.getModules(classLoader)} a secas.
 * Eso instala un {@code PolymorphicTypeValidator} que solo admite clases de Spring Security — y
 * en el mismo hash de Redis, Spring Session escribe ADEMAS su propia metadata. Resultado en
 * ejecucion:
 *
 * <pre>
 * SerializationException: Could not resolve type id 'java.lang.Long' as a subtype of
 * `java.lang.Object`: Configured `PolymorphicTypeValidator` denied resolution
 *   (through reference chain: java.util.HashMap["lastAccessedTime"])
 *   at RedisIndexedSessionRepository.onMessage
 * </pre>
 *
 * Ese {@code onMessage} es el oyente de expiracion: el que limpia del indice las sesiones
 * vencidas. Si no puede leerlas, no las limpia, y quedan acumulandose en Redis.
 *
 * {@code MapSession} guarda {@code creationTime} y {@code lastAccessedTime} como
 * {@link Instant} y {@code maxInactiveInterval} como {@link Duration}, pero el repositorio de
 * Redis los persiste como milisegundos y segundos — de ahi el {@code Long} del error. Se admiten
 * las cuatro formas para no depender de ese detalle interno.
 *
 * <p>Ampliar la lista con escalares del JDK no reabre el agujero que el validador cierra: el
 * riesgo de la deserializacion polimorfica son las clases con efectos secundarios al
 * construirse, no un {@code Long}. Se enumeran una por una, en vez de abrir {@code java.lang.*},
 * para que agregar cualquier otra sea una decision explicita.
 */
@Configuration(proxyBeanMethods = false)
class RedisSessionConfig implements BeanClassLoaderAware {

    private ClassLoader classLoader;

    @Bean(name = "springSessionDefaultRedisSerializer")
    RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        BasicPolymorphicTypeValidator.Builder tiposDeLaSesion = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(Long.class)      // creationTime, lastAccessedTime (epoch millis)
                .allowIfSubType(Integer.class)   // maxInactiveInterval (segundos)
                .allowIfSubType(Boolean.class)
                .allowIfSubType(String.class)
                .allowIfSubType(Instant.class)   // por si el repositorio deja de aplanarlos
                .allowIfSubType(Duration.class);

        JsonMapper mapper = JsonMapper.builder()
                .addModules(SecurityJacksonModules.getModules(classLoader, tiposDeLaSesion))
                .build();
        return new JacksonJsonRedisSerializer<>(mapper, Object.class);
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }
}
