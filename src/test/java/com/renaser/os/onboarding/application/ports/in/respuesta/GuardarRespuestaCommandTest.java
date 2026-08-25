package com.renaser.os.onboarding.application.ports.in.respuesta;

import com.renaser.os.onboarding.application.ports.in.respuesta.GuardarRespuestaUseCase.GuardarRespuestaCommand;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuardarRespuestaCommandTest {

    @Test
    void construyeUnComandoValidoSinExplotar() {
        UserId usuarioId = UserId.of(UUID.randomUUID());

        var command = new GuardarRespuestaCommand(usuarioId, 1, "hola", null, null, null, null, null);

        assertThat(command.usuarioId()).isEqualTo(usuarioId);
        assertThat(command.preguntaId()).isEqualTo(1);
    }

    @Test
    void rechazaUsuarioIdNulo() {
        assertThatThrownBy(() -> new GuardarRespuestaCommand(null, 1, "hola", null, null, null, null, null))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rechazaPreguntaIdNulo() {
        UserId usuarioId = UserId.of(UUID.randomUUID());
        assertThatThrownBy(() -> new GuardarRespuestaCommand(usuarioId, null, "hola", null, null, null, null, null))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
