package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contra un Redis real (Testcontainers) — mismo patron que
 * {@link TokenResetContrasenaRedisAdapterTest}, mismo motivo: confirmar que GETDEL es atomico
 * de verdad y que el TTL nativo expira el token sin cron de purga.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TokenVerificacionEmailRedisAdapterTest {

    @Autowired
    private TokenVerificacionEmailPort tokenVerificacionEmailPort;

    @Test
    void generarYConsumirDevuelveElMismoEmail() {
        String email = "verificado@renaser.dev";

        String token = tokenVerificacionEmailPort.generar(email, Duration.ofMinutes(30));
        Optional<String> resultado = tokenVerificacionEmailPort.consumir(token);

        assertThat(resultado).contains(email);
    }

    @Test
    void consumirElMismoTokenDosVecesSoloTieneExitoLaPrimera() {
        String email = "verificado@renaser.dev";
        String token = tokenVerificacionEmailPort.generar(email, Duration.ofMinutes(30));

        Optional<String> primerIntento = tokenVerificacionEmailPort.consumir(token);
        Optional<String> segundoIntento = tokenVerificacionEmailPort.consumir(token);

        assertThat(primerIntento).contains(email);
        assertThat(segundoIntento).isEmpty();
    }

    @Test
    void consumirUnTokenQueNuncaExistioDevuelveVacio() {
        assertThat(tokenVerificacionEmailPort.consumir("token-que-nunca-se-genero")).isEmpty();
    }

    /**
     * E-152: {@code emailDe} tiene que poder leerse sin gastar el token. Es lo que permite validar
     * el alta antes de hacer trabajo que puede fallar, y consumirlo recien cuando la transaccion
     * comiteo. Si esta lectura borrara, volveriamos al bug: un alta fallida dejaba a la persona
     * sin su codigo, con el mensaje "el codigo no es valido o ya vencio".
     */
    @Test
    void emailDeLeeSinGastarElToken() {
        String email = "verificado@renaser.dev";
        String token = tokenVerificacionEmailPort.generar(email, Duration.ofMinutes(30));

        assertThat(tokenVerificacionEmailPort.emailDe(token)).contains(email);
        assertThat(tokenVerificacionEmailPort.emailDe(token))
                .as("leer dos veces sigue dando lo mismo: no borra")
                .contains(email);
        assertThat(tokenVerificacionEmailPort.consumir(token))
                .as("y despues todavia se puede consumir")
                .contains(email);
        assertThat(tokenVerificacionEmailPort.emailDe(token))
                .as("recien ahi queda vacio")
                .isEmpty();
    }

    @Test
    void emailDeConUnTokenQueNuncaExistioDevuelveVacio() {
        assertThat(tokenVerificacionEmailPort.emailDe("token-que-nunca-se-genero")).isEmpty();
    }

    @Test
    void unTokenVencidoYaNoSePuedeConsumir() throws InterruptedException {
        String token = tokenVerificacionEmailPort.generar("verificado@renaser.dev", Duration.ofMillis(500));

        Thread.sleep(900);

        assertThat(tokenVerificacionEmailPort.consumir(token)).isEmpty();
    }
}
