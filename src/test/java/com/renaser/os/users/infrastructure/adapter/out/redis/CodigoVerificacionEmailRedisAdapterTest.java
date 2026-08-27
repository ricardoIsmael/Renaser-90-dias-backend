package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.users.application.ports.out.autenticacion.CodigoVerificacionEmailPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contra Redis real — el punto central es que el limite de intentos sea de verdad (OWASP
 * Multifactor Authentication Cheat Sheet: "apply strict attempt limits"), no solo el TTL.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CodigoVerificacionEmailRedisAdapterTest {

    @Autowired
    private CodigoVerificacionEmailPort codigoVerificacionEmailPort;

    @Test
    void generarYVerificarConElCodigoCorrectoTieneExito() {
        String email = "codigo1@renaser.dev";
        String codigo = codigoVerificacionEmailPort.generarCodigo(email, Duration.ofMinutes(10));

        assertThat(codigoVerificacionEmailPort.verificarCodigo(email, codigo, 5)).isTrue();
    }

    @Test
    void elCodigoEsDe6DigitosNumericos() {
        String codigo = codigoVerificacionEmailPort.generarCodigo("codigo-forma@renaser.dev", Duration.ofMinutes(10));

        assertThat(codigo).hasSize(6).matches("\\d{6}");
    }

    @Test
    @DisplayName("un codigo correcto se consume: verificarlo dos veces la segunda falla")
    void unCodigoCorrectoEsDeUnSoloUso() {
        String email = "codigo2@renaser.dev";
        String codigo = codigoVerificacionEmailPort.generarCodigo(email, Duration.ofMinutes(10));

        boolean primerIntento = codigoVerificacionEmailPort.verificarCodigo(email, codigo, 5);
        boolean segundoIntento = codigoVerificacionEmailPort.verificarCodigo(email, codigo, 5);

        assertThat(primerIntento).isTrue();
        assertThat(segundoIntento).isFalse();
    }

    @Test
    void unCodigoIncorrectoNoTieneExito() {
        String email = "codigo3@renaser.dev";
        codigoVerificacionEmailPort.generarCodigo(email, Duration.ofMinutes(10));

        assertThat(codigoVerificacionEmailPort.verificarCodigo(email, "000000", 5)).isFalse();
    }

    @Test
    @DisplayName("verificar un codigo para un email que nunca pidio uno falla, no explota")
    void verificarSinHaberGeneradoNuncaDevuelveFalse() {
        assertThat(codigoVerificacionEmailPort.verificarCodigo("nunca-pidio@renaser.dev", "123456", 5)).isFalse();
    }

    @Test
    @DisplayName("OWASP MFA Cheat Sheet: al agotar los intentos, el codigo se invalida por "
            + "completo — ni siquiera el codigo CORRECTO sirve despues")
    void agotarLosIntentosInvalidaElCodigoAunqueDespuesSeIntenteElCorrecto() {
        String email = "codigo4@renaser.dev";
        String codigo = codigoVerificacionEmailPort.generarCodigo(email, Duration.ofMinutes(10));
        int maxIntentos = 5;

        for (int i = 0; i < maxIntentos; i++) {
            boolean resultado = codigoVerificacionEmailPort.verificarCodigo(email, "000000", maxIntentos);
            assertThat(resultado).isFalse();
        }

        // El codigo real ya no deberia funcionar: se agotaron los intentos permitidos.
        assertThat(codigoVerificacionEmailPort.verificarCodigo(email, codigo, maxIntentos)).isFalse();
    }

    @Test
    @DisplayName("intentos fallidos por debajo del maximo no invalidan el codigo real")
    void intentosFallidosPorDebajoDelMaximoNoInvalidanElCodigo() {
        String email = "codigo5@renaser.dev";
        String codigo = codigoVerificacionEmailPort.generarCodigo(email, Duration.ofMinutes(10));
        int maxIntentos = 5;

        for (int i = 0; i < maxIntentos - 1; i++) {
            codigoVerificacionEmailPort.verificarCodigo(email, "000000", maxIntentos);
        }

        assertThat(codigoVerificacionEmailPort.verificarCodigo(email, codigo, maxIntentos)).isTrue();
    }

    @Test
    @DisplayName("pedir un codigo nuevo resetea el contador de intentos del email")
    void generarUnCodigoNuevoReseteaLosIntentosFallidosDelAnterior() {
        String email = "codigo6@renaser.dev";
        String codigoViejo = codigoVerificacionEmailPort.generarCodigo(email, Duration.ofMinutes(10));
        int maxIntentos = 5;
        for (int i = 0; i < maxIntentos; i++) {
            codigoVerificacionEmailPort.verificarCodigo(email, "000000", maxIntentos);
        }
        assertThat(codigoVerificacionEmailPort.verificarCodigo(email, codigoViejo, maxIntentos))
                .as("el codigo viejo ya deberia estar invalidado por agotar intentos")
                .isFalse();

        String codigoNuevo = codigoVerificacionEmailPort.generarCodigo(email, Duration.ofMinutes(10));

        assertThat(codigoVerificacionEmailPort.verificarCodigo(email, codigoNuevo, maxIntentos)).isTrue();
    }

    @Test
    void unCodigoVencidoYaNoSePuedeVerificar() throws InterruptedException {
        String email = "codigo7@renaser.dev";
        String codigo = codigoVerificacionEmailPort.generarCodigo(email, Duration.ofMillis(500));

        Thread.sleep(900);

        assertThat(codigoVerificacionEmailPort.verificarCodigo(email, codigo, 5)).isFalse();
    }

    @Test
    void dosEmailsDistintosTienenCodigosIndependientes() {
        String codigoA = codigoVerificacionEmailPort.generarCodigo("codigo8a@renaser.dev", Duration.ofMinutes(10));
        String codigoB = codigoVerificacionEmailPort.generarCodigo("codigo8b@renaser.dev", Duration.ofMinutes(10));

        assertThat(codigoVerificacionEmailPort.verificarCodigo("codigo8a@renaser.dev", codigoB, 5)).isFalse();
        assertThat(codigoVerificacionEmailPort.verificarCodigo("codigo8a@renaser.dev", codigoA, 5)).isTrue();
    }
}
