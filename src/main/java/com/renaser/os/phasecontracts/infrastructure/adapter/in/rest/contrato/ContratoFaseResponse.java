package com.renaser.os.phasecontracts.infrastructure.adapter.in.rest.contrato;

import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosUseCase.ContratoConUrlLectura;
import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFase;
import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;

import java.time.Instant;

public record ContratoFaseResponse(String id, FasePrograma phase, String phaseLabel, String bucket,
                                    String signatureUrl, Instant signedAt) {

    static ContratoFaseResponse deFirma(ContratoFase contrato) {
        return new ContratoFaseResponse(contrato.id().toString(), contrato.fase(), contrato.fase().etiqueta(),
                contrato.bucket(), null, contrato.firmadoEn());
    }

    static ContratoFaseResponse deListado(ContratoConUrlLectura item) {
        ContratoFase contrato = item.contrato();
        return new ContratoFaseResponse(contrato.id().toString(), contrato.fase(), contrato.fase().etiqueta(),
                contrato.bucket(), item.urlLectura().toString(), contrato.firmadoEn());
    }
}
