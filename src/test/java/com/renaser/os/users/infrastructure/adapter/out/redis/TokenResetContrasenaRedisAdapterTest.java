package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.autenticacion.TokenResetContrasenaPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

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

    @Autowired
    private StringRedisTemplate redisTemplate;

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

    /**
     * Regresion de la toma de cuenta del 2026-09-18. <b>Falla contra el codigo viejo.</b>
     *
     * <p>El campo {@code token} de {@code POST /api/v1/auth/password/reset-confirm} —publico, sin
     * sesion— solo estaba anotado {@code @NotBlank}, y la clave se armaba concatenando:
     * {@code "reset-password:" + token}. Bajo ese mismo prefijo vive el contador de intentos del
     * codigo OTP ({@code reset-password:intentos:<email>}). Como consumir hace GETDEL, mandar
     * {@code token = "intentos:<email de la victima>"} BORRABA ese contador.
     *
     * <p>Sin contador, el limite de 5 intentos no se alcanza nunca: el codigo de 6 digitos queda
     * expuesto a fuerza bruta ilimitada, y acertarlo entrega el token de reset real con el que se
     * fija una contrasena nueva. Toma de cuenta completa, sin autenticar.
     */
    @Test
    void unTokenNoPuedeBorrarElContadorDeIntentosDeOtraPersona() {
        String claveDelContador = "reset-password:intentos:victima@renaser.test";
        redisTemplate.opsForValue().set(claveDelContador, "4", Duration.ofMinutes(10));

        Optional<UserId> resultado = tokenResetContrasenaPort.consumir("intentos:victima@renaser.test");

        assertThat(resultado).isEmpty();
        assertThat(redisTemplate.opsForValue().get(claveDelContador))
                .as("el contador de intentos del OTP tiene que seguir en pie")
                .isEqualTo("4");
    }

    /**
     * La otra mitad de lo mismo: {@code token = "codigo:<email>"} apuntaba al codigo OTP en si.
     * Ademas de borrarselo a la victima, el GETDEL lo DEVOLVIA, y {@code UserId.of} metia ese valor
     * en el mensaje de la excepcion que {@code GlobalExceptionHandler} escribe tal cual en el
     * cuerpo del 400: el OTP de la victima terminaba impreso en la respuesta.
     */
    @Test
    void unTokenNoPuedeLeerNiBorrarElCodigoOtpDeOtraPersona() {
        String claveDelCodigo = "reset-password:codigo:victima@renaser.test";
        redisTemplate.opsForValue().set(claveDelCodigo, "482913", Duration.ofMinutes(10));

        Optional<UserId> resultado = tokenResetContrasenaPort.consumir("codigo:victima@renaser.test");

        assertThat(resultado).isEmpty();
        assertThat(redisTemplate.opsForValue().get(claveDelCodigo)).isEqualTo("482913");
    }

    /**
     * Cinturon y tiradores para el filtrado: aunque el valor guardado bajo un token BIEN formado
     * no sea un UUID, consumir devuelve vacio en vez de dejar que la excepcion —que lleva el valor
     * adentro del mensaje— viaje hasta el cuerpo de la respuesta.
     */
    @Test
    void unValorCorruptoNoSeFiltraEnElMensajeDeError() {
        String token = tokenResetContrasenaPort.generar(UserId.of(UUID.randomUUID()), Duration.ofMinutes(30));
        redisTemplate.opsForValue().set("reset-password:" + token, "no-soy-un-uuid", Duration.ofMinutes(10));

        assertThat(tokenResetContrasenaPort.consumir(token)).isEmpty();
    }
}
