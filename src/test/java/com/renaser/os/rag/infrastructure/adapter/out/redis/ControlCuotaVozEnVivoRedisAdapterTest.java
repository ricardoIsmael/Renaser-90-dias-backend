package com.renaser.os.rag.infrastructure.adapter.out.redis;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rag.application.ports.out.cuota.ControlCuotaVozEnVivoPort;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contra Redis real (Testcontainers): la suma y el vencimiento en un solo script, un contador por
 * dia (D-162). Levanta el contexto entero, asi que tambien prueba que el WebSocket de voz en vivo
 * convive con el STOMP del chat y que el backend arranca con la voz en vivo apagada.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ControlCuotaVozEnVivoRedisAdapterTest {

    @Autowired
    private ControlCuotaVozEnVivoPort port;
    @Autowired
    private StringRedisTemplate redis;

    @Test
    @DisplayName("suma por dia, con vencimiento, y un dia no toca el contador del otro")
    void sumaPorDia() {
        UserId actor = UserId.of(UUID.randomUUID());
        LocalDate ayer = LocalDate.parse("2026-09-23");
        LocalDate hoy = LocalDate.parse("2026-09-24");

        assertThat(port.usadoEn(actor, hoy)).isEqualTo(Duration.ZERO);
        port.sumar(actor, hoy, Duration.ofSeconds(5));
        Duration total = port.sumar(actor, hoy, Duration.ofSeconds(7));
        port.sumar(actor, ayer, Duration.ofSeconds(100));

        assertThat(total).isEqualTo(Duration.ofSeconds(12));
        assertThat(port.usadoEn(actor, hoy)).isEqualTo(Duration.ofSeconds(12));
        assertThat(port.usadoEn(actor, ayer)).isEqualTo(Duration.ofSeconds(100));
        Long ttl = redis.getExpire(ControlCuotaVozEnVivoRedisAdapter.clave(actor, hoy), TimeUnit.SECONDS);
        assertThat(ttl).isPositive().isLessThanOrEqualTo(ControlCuotaVozEnVivoRedisAdapter.VIGENCIA.toSeconds());
    }
}
