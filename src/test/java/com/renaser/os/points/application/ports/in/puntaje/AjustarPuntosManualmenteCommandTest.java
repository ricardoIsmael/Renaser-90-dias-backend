package com.renaser.os.points.application.ports.in.puntaje;

import com.renaser.os.points.application.ports.in.puntaje.AjustarPuntosManualmenteUseCase.AjustarPuntosManualmenteCommand;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AjustarPuntosManualmenteCommandTest {

    @Test
    void construyeUnComandoValidoSinExplotar() {
        UserId participanteId = UserId.of(UUID.randomUUID());
        UserId actorId = UserId.of(UUID.randomUUID());

        var command = new AjustarPuntosManualmenteCommand(participanteId, -5, "correccion", actorId);

        assertThat(command.delta()).isEqualTo(-5);
    }

    @Test
    void rechazaParticipanteIdNulo() {
        UserId actorId = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> new AjustarPuntosManualmenteCommand(null, 5, null, actorId))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rechazaActorIdNulo() {
        UserId participanteId = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> new AjustarPuntosManualmenteCommand(participanteId, 5, null, null))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("el comando no tiene campo `motivo`: MANUAL_ADJUSTMENT se fuerza server-side (CLAUDE.MD §5.3.3)")
    void noTieneCampoMotivoInyectable() {
        assertThat(AjustarPuntosManualmenteCommand.class.getRecordComponents()).hasSize(4);
        assertThat(Arrays.stream(AjustarPuntosManualmenteCommand.class.getRecordComponents())
                .map(RecordComponent::getName))
                .doesNotContain("motivo");
    }
}
