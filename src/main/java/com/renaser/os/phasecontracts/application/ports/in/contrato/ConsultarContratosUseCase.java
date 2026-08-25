package com.renaser.os.phasecontracts.application.ports.in.contrato;

import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFase;
import com.renaser.os.shared.domain.UserId;

import java.net.URI;
import java.util.List;

public interface ConsultarContratosUseCase {

    List<ContratoConUrlLectura> consultarDeParticipante(UserId participanteId);

    record ContratoConUrlLectura(ContratoFase contrato, URI urlLectura) {
    }
}
