package com.renaser.os.users.application.ports.in.accountrequest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmitAccountRequestCommandTest {

    @Test
    void construyeUnComandoConDatosValidosSinExplotar() {
        var command = new SubmitAccountRequestUseCase.SubmitAccountRequestCommand(
                "valido@renaser.com", "Ana", "+51999999999", "Lima", "token-verificacion",
                "una-contrasena-de-12", "127.0.0.1");

        assertThat(command.email()).isEqualTo("valido@renaser.com");
    }

    @Test
    void aceptaContrasenaNullParaElAltaPorProveedorSocial() {
        var command = new SubmitAccountRequestUseCase.SubmitAccountRequestCommand(
                "valido@renaser.com", "Ana", "+51999999999", "Lima", "token-verificacion",
                null, "127.0.0.1");

        assertThat(command.contrasena()).isNull();
    }

    @Test
    void rechazaUnaContrasenaMasCortaQueElMinimo() {
        assertThatThrownBy(() -> new SubmitAccountRequestUseCase.SubmitAccountRequestCommand(
                "valido@renaser.com", "Ana", "+51999999999", "Lima", "token-verificacion",
                "corta", "127.0.0.1"))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
    }

    @Test
    void noFiltraLaContrasenaNiElTokenEnElToString() {
        var command = new SubmitAccountRequestUseCase.SubmitAccountRequestCommand(
                "valido@renaser.com", "Ana", "+51999999999", "Lima", "token-secreto",
                "una-contrasena-de-12", "127.0.0.1");

        assertThat(command.toString()).doesNotContain("una-contrasena-de-12", "token-secreto");
    }
}
