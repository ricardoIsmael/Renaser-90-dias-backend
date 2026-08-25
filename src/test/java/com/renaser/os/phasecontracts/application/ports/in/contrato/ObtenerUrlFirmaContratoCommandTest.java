package com.renaser.os.phasecontracts.application.ports.in.contrato;

import com.renaser.os.phasecontracts.application.ports.in.contrato.ObtenerUrlFirmaContratoUseCase.ObtenerUrlFirmaContratoCommand;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObtenerUrlFirmaContratoCommandTest {

    @Test
    void construyeUnComandoValidoSinExplotar() {
        UserId participanteId = UserId.of(UUID.randomUUID());

        var command = new ObtenerUrlFirmaContratoCommand(participanteId);

        assertThat(command.participanteId()).isEqualTo(participanteId);
    }

    @Test
    void rechazaParticipanteIdNulo() {
        assertThatThrownBy(() -> new ObtenerUrlFirmaContratoCommand(null))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("el comando SOLO tiene participanteId: no hay campo `bucket`/`ruta` inyectable (D-34, CLAUDE.MD §5.3.3)")
    void noTieneCamposDeRutaInyectables() {
        assertThat(ObtenerUrlFirmaContratoCommand.class.getRecordComponents()).hasSize(1);
        assertThat(ObtenerUrlFirmaContratoCommand.class.getRecordComponents()[0].getName()).isEqualTo("participanteId");
    }
}
