package com.renaser.os.phasecontracts.application.ports.out.contrato;

import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFase;
import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

public interface LoadContratoPort {

    Optional<ContratoFase> porParticipanteYFase(UserId participanteId, FasePrograma fase);

    /** Todos los pactos firmados de un participante, mas antiguo primero. */
    List<ContratoFase> todosDeParticipante(UserId participanteId);
}
