package com.renaser.os.phasecontracts.application.services;

import com.renaser.os.phasecontracts.api.ContratosDeFaseDelParticipanteFinder;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosPendientesUseCase;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosPendientesUseCase.ContratoPendiente;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosUseCase;
import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementa {@link ContratosDeFaseDelParticipanteFinder} (2026-09-23) con los dos casos de uso que
 * sirven {@code GET /phase-contracts} y {@code GET /phase-contracts/pending}. Solo traduce a tipos de
 * Java: la fase viaja como numero y etiqueta, nunca el enum, y la URL prefirmada se descarta.
 */
@Service
class ContratosDeFaseDelParticipanteService implements ContratosDeFaseDelParticipanteFinder {

    private final ConsultarContratosUseCase consultarContratos;
    private final ConsultarContratosPendientesUseCase consultarPendiente;

    ContratosDeFaseDelParticipanteService(ConsultarContratosUseCase consultarContratos,
                                          ConsultarContratosPendientesUseCase consultarPendiente) {
        this.consultarContratos = consultarContratos;
        this.consultarPendiente = consultarPendiente;
    }

    @Override
    public ContratosDeFase delParticipante(UserId participanteId) {
        ContratoPendiente pendiente = consultarPendiente.consultarPendiente(participanteId);
        List<FaseDelContrato> firmados = consultarContratos.consultarDeParticipante(participanteId).stream()
                .map(conUrl -> aFase(conUrl.contrato().fase()))
                .toList();
        return new ContratosDeFase(firmados, pendiente.pendiente() ? aFase(pendiente.fase()) : null);
    }

    private static FaseDelContrato aFase(FasePrograma fase) {
        return new FaseDelContrato(fase.numero(), fase.etiqueta());
    }
}
