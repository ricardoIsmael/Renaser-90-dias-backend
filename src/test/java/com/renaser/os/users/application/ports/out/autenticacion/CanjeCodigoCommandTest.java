package com.renaser.os.users.application.ports.out.autenticacion;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanjeCodigoCommandTest {

    @Test
    void construyeUnComandoConDatosValidosSinExplotar() {
        var command = new CanjeCodigoCommand("un-code", "un-code-verifier", "https://app.renaser.dev/callback");

        assertThat(command.code()).isEqualTo("un-code");
        assertThat(command.redirectUri()).isEqualTo("https://app.renaser.dev/callback");
    }

    @Test
    void rechazaCodeVacio() {
        assertThatThrownBy(() -> new CanjeCodigoCommand("", "verifier", "https://app.renaser.dev/callback"))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rechazaCodeVerifierVacio() {
        assertThatThrownBy(() -> new CanjeCodigoCommand("code", " ", "https://app.renaser.dev/callback"))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rechazaRedirectUriVacio() {
        assertThatThrownBy(() -> new CanjeCodigoCommand("code", "verifier", null))
                .isInstanceOf(ConstraintViolationException.class);
    }

    /** El code y el code_verifier son credenciales de un solo uso: nunca en un toString(). */
    @Test
    void toStringNuncaExponeCodeNiCodeVerifier() {
        var command = new CanjeCodigoCommand("secreto-code", "secreto-verifier", "https://app.renaser.dev/callback");

        assertThat(command.toString())
                .doesNotContain("secreto-code")
                .doesNotContain("secreto-verifier")
                .contains("https://app.renaser.dev/callback");
    }
}
