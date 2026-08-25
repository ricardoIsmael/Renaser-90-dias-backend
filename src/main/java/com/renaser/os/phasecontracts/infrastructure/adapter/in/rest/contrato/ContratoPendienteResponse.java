package com.renaser.os.phasecontracts.infrastructure.adapter.in.rest.contrato;

import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosPendientesUseCase.ContratoPendiente;
import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;

public record ContratoPendienteResponse(boolean pending, FasePrograma phase, String phaseLabel) {

    static ContratoPendienteResponse from(ContratoPendiente pendiente) {
        return new ContratoPendienteResponse(pendiente.pendiente(), pendiente.fase(), pendiente.etiqueta());
    }
}
