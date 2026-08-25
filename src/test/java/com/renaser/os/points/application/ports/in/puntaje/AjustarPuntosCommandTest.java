package com.renaser.os.points.application.ports.in.puntaje;

import com.renaser.os.points.application.ports.in.puntaje.AjustarPuntosUseCase.AjustarPuntosCommand;
import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AjustarPuntosCommandTest {

    @Test
    void construyeUnComandoValidoSinExplotar() {
        UserId participanteId = UserId.of(UUID.randomUUID());

        var command = new AjustarPuntosCommand(participanteId, MotivoPuntos.HABIT_COMPLETED, 10, "ok");

        assertThat(command.delta()).isEqualTo(10);
    }

    @Test
    void rechazaParticipanteIdNulo() {
        assertThatThrownBy(() -> new AjustarPuntosCommand(null, MotivoPuntos.HABIT_COMPLETED, 10, null))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rechazaMotivoNulo() {
        UserId participanteId = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> new AjustarPuntosCommand(participanteId, null, 10, null))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void notaEsOpcional() {
        UserId participanteId = UserId.of(UUID.randomUUID());

        var command = new AjustarPuntosCommand(participanteId, MotivoPuntos.MISSED_HABIT, -10, null);

        assertThat(command.nota()).isNull();
    }
}
