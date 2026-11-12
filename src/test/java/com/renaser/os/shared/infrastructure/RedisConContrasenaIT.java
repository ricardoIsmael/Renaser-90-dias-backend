package com.renaser.os.shared.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El backend conecta a Redis con o sin contraseña, leyéndola de la configuración (spec 050 FR-010, tramo A1).
 *
 * <p>Es lo que hace segura la salida a producción en dos tiempos: primero se despliega el backend con
 * {@code REDIS_USERNAME=default} y {@code REDIS_PASSWORD} mientras Redis todavía NO exige clave, y recién
 * después se la exige ({@code infra/redis/contrasena.sh}). Si alguna de estas pruebas se rompe, ese orden ya
 * no es seguro.
 *
 * <p>Usa el {@code application.yaml} de {@code main} por ruta (el de test lo reemplaza en el classpath) con las
 * variables de entorno que el backend recibe de Parameter Store, y la misma autoconfiguración de Spring Boot
 * que usa la aplicación: ninguna conexión armada a mano. Redis es real (Testcontainers), una instancia por
 * prueba, con o sin {@code requirepass}.
 */
class RedisConContrasenaIT {

    private static final String CLAVE = "clave-solo-para-la-prueba-" + UUID.randomUUID();
    private static final DockerImageName IMAGEN = DockerImageName.parse("redis:7-alpine");

    private static GenericContainer<?> redisAbierto() {
        return new GenericContainer<>(IMAGEN).withExposedPorts(6379)
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));
    }

    private static GenericContainer<?> redisQueExigeClave() {
        return new GenericContainer<>(IMAGEN).withExposedPorts(6379)
                .withCommand("redis-server", "--requirepass", CLAVE)
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));
    }

    /** La aplicación tal como arranca en producción para Redis: application.yaml + lo que llega como variable. */
    private static ApplicationContextRunner laApp(GenericContainer<?> redis, Map<String, Object> deParameterStore) {
        Map<String, Object> entorno = new HashMap<>(deParameterStore);
        entorno.put("REDIS_HOST", redis.getHost());
        entorno.put("REDIS_PORT", String.valueOf(redis.getMappedPort(6379)));
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
                .withInitializer(contexto -> {
                    MutablePropertySources fuentes = contexto.getEnvironment().getPropertySources();
                    fuentes.addFirst(new MapPropertySource("parameter-store-de-la-prueba", entorno));
                    for (PropertySource<?> fuente : yamlDeMain()) {
                        fuentes.addLast(fuente);
                    }
                });
    }

    private static List<PropertySource<?>> yamlDeMain() {
        try {
            return new YamlPropertySourceLoader().load("application-de-main",
                    new FileSystemResource("src/main/resources/application.yaml"));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer src/main/resources/application.yaml", e);
        }
    }

    private static String ping(StringRedisTemplate plantilla) {
        return plantilla.execute((RedisConnection conexion) -> conexion.ping());
    }

    @Test
    // spec 050 FR-010
    @DisplayName("sin contraseña configurada, el backend conecta a un Redis abierto (como hoy)")
    void sinContrasenaConectaAUnRedisAbierto() {
        try (GenericContainer<?> redis = redisAbierto()) {
            redis.start();
            laApp(redis, Map.of()).run(contexto -> assertThat(ping(contexto.getBean(StringRedisTemplate.class)))
                    .isEqualTo("PONG"));
        }
    }

    @Test
    // spec 050 FR-010
    @DisplayName("con usuario «default» y contraseña, conecta a un Redis que todavía NO la exige: paso 2 de la salida")
    void conCredencialesConectaAUnRedisTodaviaAbierto() {
        try (GenericContainer<?> redis = redisAbierto()) {
            redis.start();
            laApp(redis, Map.of("REDIS_USERNAME", "default", "REDIS_PASSWORD", CLAVE))
                    .run(contexto -> assertThat(ping(contexto.getBean(StringRedisTemplate.class))).isEqualTo("PONG"));
        }
    }

    @Test
    // spec 050 FR-010
    @DisplayName("con la contraseña de Parameter Store, conecta a un Redis que la exige (con y sin usuario)")
    void conContrasenaConectaAUnRedisQueLaExige() {
        try (GenericContainer<?> redis = redisQueExigeClave()) {
            redis.start();
            laApp(redis, Map.of("REDIS_USERNAME", "default", "REDIS_PASSWORD", CLAVE))
                    .run(contexto -> assertThat(ping(contexto.getBean(StringRedisTemplate.class))).isEqualTo("PONG"));
            laApp(redis, Map.of("REDIS_PASSWORD", CLAVE))
                    .run(contexto -> assertThat(ping(contexto.getBean(StringRedisTemplate.class))).isEqualTo("PONG"));
        }
    }

    @Test
    // spec 050 FR-010
    @DisplayName("un backend SIN la contraseña no entra a un Redis que la exige: por eso se despliega ANTES de exigirla")
    void sinContrasenaNoEntraAUnRedisQueLaExige() {
        try (GenericContainer<?> redis = redisQueExigeClave()) {
            redis.start();
            laApp(redis, Map.of()).run(contexto -> assertThatThrownBy(() -> ping(contexto.getBean(StringRedisTemplate.class)))
                    .hasStackTraceContaining("NOAUTH"));
        }
    }

    @Test
    // spec 050 FR-010
    @DisplayName("exigir la contraseña en caliente no corta la conexión abierta, y la conexión nueva se autentica sola")
    void exigirLaContrasenaEnCalienteNoCortaLaConexion() {
        try (GenericContainer<?> redis = redisAbierto()) {
            redis.start();
            laApp(redis, Map.of("REDIS_USERNAME", "default", "REDIS_PASSWORD", CLAVE)).run(contexto -> {
                StringRedisTemplate plantilla = contexto.getBean(StringRedisTemplate.class);
                plantilla.opsForValue().set("sesion:de-prueba", "viva");
                assertThat(ping(plantilla)).isEqualTo("PONG");

                ejecutarEnRedis(redis, "CONFIG", "SET", "requirepass", CLAVE);

                assertThat(plantilla.opsForValue().get("sesion:de-prueba")).isEqualTo("viva");
                contexto.getBean(LettuceConnectionFactory.class).resetConnection();
                assertThat(plantilla.opsForValue().get("sesion:de-prueba"))
                        .as("la conexión nueva tiene que autenticarse con la clave que ya traía")
                        .isEqualTo("viva");
            });
        }
    }

    private static void ejecutarEnRedis(GenericContainer<?> redis, String... orden) {
        try {
            String[] comando = new String[orden.length + 1];
            comando[0] = "redis-cli";
            System.arraycopy(orden, 0, comando, 1, orden.length);
            assertThat(redis.execInContainer(comando).getExitCode()).isZero();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo ejecutar redis-cli en el contenedor", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido mientras se ejecutaba redis-cli", e);
        }
    }
}
