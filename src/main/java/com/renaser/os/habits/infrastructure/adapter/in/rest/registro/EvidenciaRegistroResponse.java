package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.renaser.os.evidence.api.RegistrarEvidenciaPort.EvidenciaRegistrada;

import java.util.UUID;

public record EvidenciaRegistroResponse(UUID id, String estadoValidacion) {

    public static EvidenciaRegistroResponse from(EvidenciaRegistrada r) {
        return new EvidenciaRegistroResponse(r.id(), r.estadoValidacion().name());
    }
}
