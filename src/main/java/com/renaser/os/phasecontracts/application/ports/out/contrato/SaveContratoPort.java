package com.renaser.os.phasecontracts.application.ports.out.contrato;

import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFase;

public interface SaveContratoPort {

    ContratoFase save(ContratoFase contrato);
}
