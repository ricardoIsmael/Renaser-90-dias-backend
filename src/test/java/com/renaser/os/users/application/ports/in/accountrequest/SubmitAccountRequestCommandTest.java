package com.renaser.os.users.application.ports.in.accountrequest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubmitAccountRequestCommandTest {

    @Test
    void construyeUnComandoConDatosValidosSinExplotar() {
        var command = new SubmitAccountRequestUseCase.SubmitAccountRequestCommand(
                "valido@renaser.com", "Ana", "+51999999999", "Lima", "token-verificacion", "127.0.0.1");

        assertThat(command.email()).isEqualTo("valido@renaser.com");
    }
}
