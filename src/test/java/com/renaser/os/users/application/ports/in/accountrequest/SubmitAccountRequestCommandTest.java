package com.renaser.os.users.application.ports.in.accountrequest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubmitAccountRequestCommandTest {

    @Test
    void construyeUnComandoConDatosValidosSinExplotar() {
        var command = new SubmitAccountRequestUseCase.SubmitAccountRequestCommand(
                "11111111-1111-1111-1111-111111111111", "valido@renaser.com", "Ana", "+51999999999",
                "Lima", "127.0.0.1");

        assertThat(command.supabaseUserId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(command.email()).isEqualTo("valido@renaser.com");
    }
}
