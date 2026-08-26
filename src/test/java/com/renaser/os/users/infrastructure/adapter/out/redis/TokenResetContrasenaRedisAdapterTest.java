package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.autenticacion.TokenResetContrasenaPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contra un Redis real (Testcontainers): confirma lo que un mock no puede — que
 * {@code getAndDelete} es de verdad atomico (GETDEL) y que el TTL nativo de Redis expira el
 * token sin que nadie tenga que purgarlo.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TokenResetContrasenaRedisAdapterTest {

    @Autowired
    private TokenResetContrasenaPort tokenResetContrasenaPort;

    @Test
    void generarYConsumirDevuelveElMismoUsuarioId() {
        UserId usuarioId = UserId.of(UUID.randomUUID());

        String token = tokenResetContrasenaPort.generar(usuarioId, Duration.ofMinutes(30));
        Optional<UserId> resultado = tokenResetContrasenaPort.consumir(token);

        assertThat(resultado).contains(usuarioId);
    }

    /**
     * El requisito central del alcance: "un solo uso" tiene que ser atomico. Consumir el mismo
     * token dos veces seguidas — la segunda debe encontrar la clave ya borrada por la primera.
     */
    @Test
    void consumirElMismoTokenDosVecesSoloTieneExitoLaPrimera() {
        UserId usuarioId = UserId.of(UUID.randomUUID());
        String token = tokenResetContrasenaPort.generar(usuarioId, Duration.ofMinutes(30));

        Optional<UserId> primerIntento = tokenResetContrasenaPort.consumir(token);
        Optional<UserId> segundoIntento = tokenResetContrasenaPort.consumir(token);

        assertThat(primerIntento).contains(usuarioId);
        assertThat(segundoIntento).isEmpty();
    }

    @Test
    void consumirUnTokenQueNuncaExistioDevuelveVacio() {
        Optional<UserId> resultado = tokenResetContrasenaPort.consumir("token-que-nunca-se-genero");

        assertThat(resultado).isEmpty();
    }

    @Test
    void unTokenVencidoYaNoSePuedeConsumir() throws InterruptedException {
        UserId usuarioId = UserId.of(UUID.randomUUID());
        String token = tokenResetContrasenaPort.generar(usuarioId, Duration.ofMillis(500));

        Thread.sleep(900);

        assertThat(tokenResetContrasenaPort.consumir(token)).isEmpty();
    }

    @Test
    void dosTokensDelMismoUsuarioSonIndependientes() {
        UserId usuarioId = UserId.of(UUID.randomUUID());
        String primerToken = tokenResetContrasenaPort.generar(usuarioId, Duration.ofMinutes(30));
        String segundoToken = tokenResetContrasenaPort.generar(usuarioId, Duration.ofMinutes(30));

        assertThat(tokenResetContrasenaPort.consumir(primerToken)).contains(usuarioId);
        // El primero se consumio; el segundo sigue vivo por su cuenta.
        assertThat(tokenResetContrasenaPort.consumir(segundoToken)).contains(usuarioId);
    }
}
