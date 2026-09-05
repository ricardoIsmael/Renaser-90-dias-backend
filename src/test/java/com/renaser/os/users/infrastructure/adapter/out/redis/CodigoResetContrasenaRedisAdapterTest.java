package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.users.application.ports.out.autenticacion.CodigoResetContrasenaPort;
import com.renaser.os.users.application.ports.out.autenticacion.CodigoVerificacionEmailPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contra Redis real. La mecanica (un solo uso, limite de intentos, TTL) ya esta probada a fondo
 * en {@link CodigoVerificacionEmailRedisAdapterTest} sobre la misma clase compartida
 * ({@code AlmacenCodigoNumericoRedis}); lo que este test cubre es lo que SOLO este adaptador
 * puede romper: que el codigo de reset viva en su propio espacio de claves y no se cruce con el
 * del alta (D-102).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CodigoResetContrasenaRedisAdapterTest {

    private static final Duration VIGENCIA = Duration.ofMinutes(10);
    private static final int MAX_INTENTOS = 5;

    @Autowired
    private CodigoResetContrasenaPort codigoResetContrasenaPort;
    @Autowired
    private CodigoVerificacionEmailPort codigoVerificacionEmailPort;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void generarYVerificarConElCodigoCorrectoTieneExitoYEsDeUnSoloUso() {
        String email = "reset1@renaser.dev";
        String codigo = codigoResetContrasenaPort.generarCodigo(email, VIGENCIA);

        assertThat(codigo).hasSize(6).matches("\\d{6}");
        assertThat(codigoResetContrasenaPort.verificarCodigo(email, codigo, MAX_INTENTOS)).isTrue();
        assertThat(codigoResetContrasenaPort.verificarCodigo(email, codigo, MAX_INTENTOS)).isFalse();
    }

    @Test
    @DisplayName("el codigo vive bajo reset-password:codigo:, no bajo el prefijo del alta")
    void guardaBajoSuPropioPrefijo() {
        String email = "reset-prefijo@renaser.dev";
        String codigo = codigoResetContrasenaPort.generarCodigo(email, VIGENCIA);

        assertThat(redisTemplate.opsForValue().get("reset-password:codigo:" + email)).isEqualTo(codigo);
        assertThat(redisTemplate.hasKey("email-verification:codigo:" + email)).isFalse();
    }

    @Test
    @DisplayName("un codigo emitido para VERIFICAR el correo (alta) no sirve para RESETEAR la contrasena")
    void elCodigoDelAltaNoSirveParaElReset() {
        String email = "cruce1@renaser.dev";
        String codigoDelAlta = codigoVerificacionEmailPort.generarCodigo(email, VIGENCIA);

        assertThat(codigoResetContrasenaPort.verificarCodigo(email, codigoDelAlta, MAX_INTENTOS)).isFalse();
        // Y el intento fallido contra el reset no le gasto nada al codigo del alta.
        assertThat(codigoVerificacionEmailPort.verificarCodigo(email, codigoDelAlta, MAX_INTENTOS)).isTrue();
    }

    @Test
    @DisplayName("pedir un codigo de reset no invalida un codigo de alta vivo para el mismo correo, ni al reves")
    void pedirUnoNoInvalidaAlOtro() {
        String email = "cruce2@renaser.dev";
        String codigoDelAlta = codigoVerificacionEmailPort.generarCodigo(email, VIGENCIA);
        String codigoDeReset = codigoResetContrasenaPort.generarCodigo(email, VIGENCIA);

        assertThat(codigoVerificacionEmailPort.verificarCodigo(email, codigoDelAlta, MAX_INTENTOS)).isTrue();
        assertThat(codigoResetContrasenaPort.verificarCodigo(email, codigoDeReset, MAX_INTENTOS)).isTrue();
    }

    @Test
    @DisplayName("agotar los intentos invalida el codigo entero, igual que en el alta")
    void agotarLosIntentosInvalidaElCodigo() {
        String email = "reset-intentos@renaser.dev";
        String codigo = codigoResetContrasenaPort.generarCodigo(email, VIGENCIA);

        for (int i = 0; i < MAX_INTENTOS; i++) {
            assertThat(codigoResetContrasenaPort.verificarCodigo(email, "000000", MAX_INTENTOS)).isFalse();
        }

        assertThat(codigoResetContrasenaPort.verificarCodigo(email, codigo, MAX_INTENTOS)).isFalse();
        assertThat(redisTemplate.hasKey("reset-password:intentos:" + email)).isFalse();
    }
}
