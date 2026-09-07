package com.renaser.os;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Un solo Postgres para los tests, con pgvector: es el mismo motor que usa RAG (§6),
 * asi que no hace falta un segundo contenedor. Initializr genero dos beans
 * @ServiceConnection en conflicto; se dejo solo este.
 *
 * <p>Redis se agrega para `chat` (Ola 4, necesita Redis Pub/Sub para el fanout entre
 * instancias — CLAUDE.MD §5.2.1). `@ServiceConnection` autoconfigura
 * `spring.data.redis.host/port` contra el contenedor, sin tocar el bean de Postgres de
 * arriba.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        requireCloudWhenRequested();
        return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16")
                .asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    @ServiceConnection
    RedisContainer redisContainer() {
        requireCloudWhenRequested();
        return new RedisContainer(DockerImageName.parse("redis:7-alpine"));
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
