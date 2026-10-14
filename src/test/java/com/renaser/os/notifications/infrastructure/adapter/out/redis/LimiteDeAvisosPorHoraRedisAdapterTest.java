package com.renaser.os.notifications.infrastructure.adapter.out.redis;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.notifications.application.ports.out.limite.LimiteDeAvisosPorHoraPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** El tope de avisos por hora contra Redis real (Testcontainers): la cuenta y el {@code NX} van en un solo script. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LimiteDeAvisosPorHoraRedisAdapterTest {

    private static final Instant INICIO = Instant.parse("2026-10-13T03:00:00Z");

    @Autowired
    private LimiteDeAvisosPorHoraPort limite;
    @Autowired
    private StringRedisTemplate redis;

    private final String grupo = "prueba-" + UUID.randomUUID();

    @AfterEach
    void limpiar() {
        redis.delete(LimiteDeAvisosPorHoraRedisAdapter.PREFIJO + grupo);
    }

    @Test
    @DisplayName("salen 4 en la hora; el quinto no y no ocupa lugar; cuando el primero sale de la hora entra otro")
    void cuatroPorHora() {
        for (int i = 0; i < 4; i++) {
            assertThat(limite.admite(grupo, UUID.randomUUID(), INICIO.plus(Duration.ofMinutes(i)), 4)).isTrue();
        }
        assertThat(limite.admite(grupo, UUID.randomUUID(), INICIO.plus(Duration.ofMinutes(10)), 4)).isFalse();
        assertThat(limite.admite(grupo, UUID.randomUUID(), INICIO.plus(Duration.ofMinutes(60)).plusMillis(1), 4))
                .as("el del minuto 0 ya salió de la hora, y el omitido no cuenta").isTrue();
        assertThat(limite.admite(grupo, UUID.randomUUID(), INICIO.plus(Duration.ofMinutes(60)).plusMillis(2), 4))
                .isFalse();
    }

    @Test
    @DisplayName("idempotente: el mismo aviso dos veces da la misma respuesta y ocupa un solo lugar")
    void idempotente() {
        UUID mismo = UUID.randomUUID();
        assertThat(limite.admite(grupo, mismo, INICIO, 1)).isTrue();
        assertThat(limite.admite(grupo, mismo, INICIO, 1)).isTrue();
        assertThat(limite.admite(grupo, UUID.randomUUID(), INICIO.plusSeconds(1), 1)).isFalse();
    }
}
