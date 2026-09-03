package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.users.application.ports.out.autenticacion.RegistroPendienteSocial;
import com.renaser.os.users.application.ports.out.autenticacion.TokenRegistroPendienteSocialPort;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contra un Redis real (Testcontainers): confirma que {@code getAndDelete} es de verdad atomico
 * (GETDEL), que el TTL nativo de Redis expira el registro sin que nadie tenga que purgarlo, y
 * que los 4 campos de {@link RegistroPendienteSocial} sobreviven la ida y vuelta por el
 * separador de control (docs/MODULO_AUTH.md §6.10).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TokenRegistroPendienteSocialRedisAdapterTest {

    @Autowired
    private TokenRegistroPendienteSocialPort tokenRegistroPendienteSocialPort;

    private static RegistroPendienteSocial registro() {
        return new RegistroPendienteSocial(ProveedorIdentidad.GOOGLE, "google-sub-redis-test",
                "redis-test@renaser.dev", "Persona De Prueba");
    }

    @Test
    void generarYConsumirDevuelveElMismoRegistro() {
        RegistroPendienteSocial registro = registro();

        String token = tokenRegistroPendienteSocialPort.generar(registro, Duration.ofMinutes(10));
        Optional<RegistroPendienteSocial> resultado = tokenRegistroPendienteSocialPort.consumir(token);

        assertThat(resultado).contains(registro);
    }

    /**
     * El requisito central del alcance: "un solo uso" tiene que ser atomico. Consumir el mismo
     * token dos veces seguidas — la segunda debe encontrar la clave ya borrada por la primera.
     */
    @Test
    void consumirElMismoTokenDosVecesSoloTieneExitoLaPrimera() {
        String token = tokenRegistroPendienteSocialPort.generar(registro(), Duration.ofMinutes(10));

        Optional<RegistroPendienteSocial> primerIntento = tokenRegistroPendienteSocialPort.consumir(token);
        Optional<RegistroPendienteSocial> segundoIntento = tokenRegistroPendienteSocialPort.consumir(token);

        assertThat(primerIntento).isPresent();
        assertThat(segundoIntento).isEmpty();
    }

    @Test
    void consumirUnTokenQueNuncaExistioDevuelveVacio() {
        Optional<RegistroPendienteSocial> resultado =
                tokenRegistroPendienteSocialPort.consumir("token-que-nunca-se-genero");

        assertThat(resultado).isEmpty();
    }

    @Test
    void unTokenVencidoYaNoSePuedeConsumir() throws InterruptedException {
        String token = tokenRegistroPendienteSocialPort.generar(registro(), Duration.ofMillis(500));

        Thread.sleep(900);

        assertThat(tokenRegistroPendienteSocialPort.consumir(token)).isEmpty();
    }

    @Test
    void unNombreConCaracteresRarosSobreviveLaIdaYVuelta() {
        RegistroPendienteSocial registro = new RegistroPendienteSocial(ProveedorIdentidad.APPLE,
                "apple-sub-caracteres", "acentos@renaser.dev", "María José Ñáñez-O'Brien");

        String token = tokenRegistroPendienteSocialPort.generar(registro, Duration.ofMinutes(10));

        assertThat(tokenRegistroPendienteSocialPort.consumir(token)).contains(registro);
    }

    @Test
    void dosRegistrosSonIndependientes() {
        RegistroPendienteSocial primero = registro();
        RegistroPendienteSocial segundo = new RegistroPendienteSocial(ProveedorIdentidad.APPLE,
                "apple-sub-otro", "otro@renaser.dev", "Otra Persona");
        String primerToken = tokenRegistroPendienteSocialPort.generar(primero, Duration.ofMinutes(10));
        String segundoToken = tokenRegistroPendienteSocialPort.generar(segundo, Duration.ofMinutes(10));

        assertThat(tokenRegistroPendienteSocialPort.consumir(primerToken)).contains(primero);
        // El primero se consumio; el segundo sigue vivo por su cuenta.
        assertThat(tokenRegistroPendienteSocialPort.consumir(segundoToken)).contains(segundo);
    }
}
