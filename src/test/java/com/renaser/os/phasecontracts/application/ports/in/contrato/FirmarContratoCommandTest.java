package com.renaser.os.phasecontracts.application.ports.in.contrato;

import com.renaser.os.phasecontracts.application.ports.in.contrato.FirmarContratoUseCase.FirmarContratoCommand;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirmarContratoCommandTest {

    @Test
    void construyeUnComandoValidoSinExplotar() {
        UserId participanteId = UserId.of(UUID.randomUUID());

        var command = new FirmarContratoCommand(participanteId);

        assertThat(command.participanteId()).isEqualTo(participanteId);
    }

    @Test
    void rechazaParticipanteIdNulo() {
        assertThatThrownBy(() -> new FirmarContratoCommand(null))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("el comando SOLO tiene participanteId: no hay campo `fase` para inyectar (CLAUDE.MD §5.3.3)")
    void noTieneCampoFaseInyectable() {
        assertThat(FirmarContratoCommand.class.getRecordComponents()).hasSize(1);
        assertThat(FirmarContratoCommand.class.getRecordComponents()[0].getName()).isEqualTo("participanteId");
    }
}
