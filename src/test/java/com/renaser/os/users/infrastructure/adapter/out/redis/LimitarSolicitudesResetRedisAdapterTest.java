package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LimitarSolicitudesResetRedisAdapterTest {

    @Autowired
    private LimitarSolicitudesResetPort limitarSolicitudesResetPort;

    @Test
    void permiteHastaElMaximoYDespuesRechaza() {
        String clave = "email:limite-" + UUID.randomUUID();

        assertThat(limitarSolicitudesResetPort.registrarIntento(clave, Duration.ofMinutes(1), 3)).isTrue();
        assertThat(limitarSolicitudesResetPort.registrarIntento(clave, Duration.ofMinutes(1), 3)).isTrue();
        assertThat(limitarSolicitudesResetPort.registrarIntento(clave, Duration.ofMinutes(1), 3)).isTrue();
        assertThat(limitarSolicitudesResetPort.registrarIntento(clave, Duration.ofMinutes(1), 3)).isFalse();
    }

    @Test
    void clavesDistintasTienenContadoresIndependientes() {
        String claveEmail = "email:independiente-" + UUID.randomUUID();
        String claveIp = "ip:independiente-" + UUID.randomUUID();

        assertThat(limitarSolicitudesResetPort.registrarIntento(claveEmail, Duration.ofMinutes(1), 1)).isTrue();
        assertThat(limitarSolicitudesResetPort.registrarIntento(claveEmail, Duration.ofMinutes(1), 1)).isFalse();
        // La clave de IP no se vio afectada por haber agotado la de email.
        assertThat(limitarSolicitudesResetPort.registrarIntento(claveIp, Duration.ofMinutes(1), 1)).isTrue();
    }

    @Test
    void laVentanaExpiraYReiniciaElContador() throws InterruptedException {
        String clave = "email:ventana-" + UUID.randomUUID();

        assertThat(limitarSolicitudesResetPort.registrarIntento(clave, Duration.ofMillis(500), 1)).isTrue();
        assertThat(limitarSolicitudesResetPort.registrarIntento(clave, Duration.ofMillis(500), 1)).isFalse();

        Thread.sleep(900);

        assertThat(limitarSolicitudesResetPort.registrarIntento(clave, Duration.ofMillis(500), 1)).isTrue();
    }
}
