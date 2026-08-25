package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.CelulaResumen;
import com.renaser.os.community.domain.model.celula.Celula;

public record CelulaResponse(String id, String name, String cohortId, String videoCallUrl, String nextSessionAt,
                              int memberCount, PerfilBasicoResponse mentor) {

    public static CelulaResponse from(CelulaResumen resumen) {
        Celula c = resumen.celula();
        return new CelulaResponse(c.id().toString(), c.nombre(), c.cohorteId().toString(), c.urlVideollamada(),
                c.proximaSesionEn() != null ? c.proximaSesionEn().toString() : null, resumen.cantidadMiembros(),
                PerfilBasicoResponse.from(resumen.mentor()));
    }
}
