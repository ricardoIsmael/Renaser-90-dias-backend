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

    /**
     * <b>Corregido 2026-09-27 (D-216, TRN-21 del e2e).</b> Esta prueba se llamaba
     * {@code noTieneCampoFaseInyectable} y exigia que el comando tuviera SOLO {@code participanteId}
     * («no hay campo `fase` para inyectar», §5.3.3). Desde D-193 se firman pactos atrasados y, con dos
     * o mas pendientes, un pedido que no dice la fase no se puede repetir sin firmar OTRO pacto: dos
     * {@code POST} seguidos firmaban el IV y el II. D-193 ya recomendaba que el cliente mande la fase.
     * La fase no se inyecta: no elige que se firma, solo confirma el que toca, y el servicio rechaza
     * cualquier otro ({@code ContratoServiceTest}). Lo que sigue sin existir es un campo de RUTA.
     */
    @Test
    @DisplayName("D-216: el comando lleva la fase que se cree firmar, y nada que elija la ruta de la firma")
    void llevaLaFaseQueSeCreeFirmarYNadaQueElijaLaRuta() {
        assertThat(FirmarContratoCommand.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("participanteId", "fase");
    }

    @Test
    @DisplayName("D-216: el pedido de siempre, sin fase, sigue siendo valido")
    void sinFaseSigueSiendoValido() {
        var command = new FirmarContratoCommand(UserId.of(UUID.randomUUID()));

        assertThat(command.fase()).isNull();
    }
}
