package com.renaser.os.phasecontracts.application.ports.in.contrato;

import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFase;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface FirmarContratoUseCase {

    ContratoFase firmar(FirmarContratoCommand command);

    record FirmarContratoCommand(@NotNull UserId participanteId) {

        public FirmarContratoCommand {
            SelfValidating.validateConstructorArgs(FirmarContratoCommand.class, participanteId);
        }
    }
}
