package com.renaser.os;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * La infraestructura de las pruebas de integración: <b>un solo Postgres por JVM</b>, con una base de
 * datos propia por contexto de Spring, y un Redis por contexto.
 *
 * <p><b>Postgres.</b> Es el mismo motor que usa RAG (§6), con pgvector, así que no hace falta un segundo
 * contenedor. Se arranca una sola vez por JVM (surefire y failsafe corren en JVM distintas: dos en
 * total) y lo detiene Ryuk al terminar. Antes cada contexto de Spring arrancaba el suyo: 50 de ellos en
 * una corrida (E-651). Cada contexto sigue teniendo su <b>propia base</b>, creada vacía y migrada por
 * Flyway como siempre, para que ninguna clase vea los datos de otro contexto: el aislamiento es el mismo
 * de antes, solo que sin pagar un contenedor por contexto. Los contextos cacheados siguen vivos
 * mientras dura la corrida y cada uno tiene su pool de Hikari, de ahí el {@code max_connections} alto.
 *
 * <p><b>Redis NO se comparte entre contextos, a propósito.</b> Guarda las sesiones, los contadores de
 * límite de intentos (por IP, y todas las pruebas llegan desde 127.0.0.1) y el Pub/Sub del chat, que
 * ninguna separación por número de base aísla: con un Redis común, los contadores de todas las clases se
 * sumarían y un contexto oiría los mensajes de otro. Arranca en 0,3 s, así que no compensa. Sigue como
 * {@code @ServiceConnection}, que autoconfigura {@code spring.data.redis.host/port}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Para dar nombre a la base de cada contexto de esta JVM. */
    private static final AtomicInteger CONTEXTOS = new AtomicInteger();

    /**
     * La base de este contexto, en el Postgres compartido. Reemplaza al {@code @ServiceConnection} del
     * contenedor (que apuntaría a la base común), y Spring Boot la usa para el {@code DataSource} y para
     * Flyway.
     */
    @Bean
    JdbcConnectionDetails baseDeEsteContexto() {
        PostgreSQLContainer postgres = PostgresCompartido.CONTENEDOR;
        String base = "renaser_ctx_" + CONTEXTOS.incrementAndGet() + "_" + UUID.randomUUID().toString().substring(0, 8);
        crearBase(postgres, base);
        String url = postgres.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + base + "$1");
        String usuario = postgres.getUsername();
        String clave = postgres.getPassword();
        return new JdbcConnectionDetails() {
            @Override
            public String getUsername() {
                return usuario;
            }

            @Override
            public String getPassword() {
                return clave;
            }

            @Override
            public String getJdbcUrl() {
                return url;
            }
        };
    }

    @Bean
    @ServiceConnection
    RedisContainer redisContainer() {
        requireCloudWhenRequested();
        return new RedisContainer(DockerImageName.parse("redis:7-alpine"));
    }

    private static void crearBase(PostgreSQLContainer postgres, String base) {
        try (Connection conexion = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()); Statement sentencia = conexion.createStatement()) {
            sentencia.execute("CREATE DATABASE " + base);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo crear la base " + base + " en el Postgres de las pruebas", e);
        }
    }

    /**
     * El Postgres único de la JVM. Se arranca la primera vez que un contexto lo pide (carga perezosa de
     * la clase) y no lo detiene ningún contexto al cerrarse: no es un bean de Spring.
     */
    private static final class PostgresCompartido {

        static final PostgreSQLContainer CONTENEDOR = arrancar();

        private static PostgreSQLContainer arrancar() {
            requireCloudWhenRequested();
            // `fsync=off` es lo que ya traía PostgreSQLContainer por defecto; se repite porque withCommand
            // reemplaza el comando entero. `max_connections`: un pool de Hikari por contexto cacheado.
            PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"))
                    .withCommand("postgres", "-c", "fsync=off", "-c", "max_connections=1000");
            postgres.start();
            return postgres;
        }
    }

    private static void requireCloudWhenRequested() {
        if (Boolean.getBoolean("renaser.tests.cloud-required")) {
            String version = org.testcontainers.DockerClientFactory.instance().client().infoCmd().exec().getServerVersion();
            requireCloudVersion(version);
        }
    }

    static void requireCloudVersion(String version) {
        if (version == null || !version.toLowerCase(java.util.Locale.ROOT).contains("testcontainerscloud")) {
            throw new IllegalStateException("Se requieren contenedores en Testcontainers Cloud; "
                    + "se cancela antes de crear contenedores en Docker local");
        }
    }

}
