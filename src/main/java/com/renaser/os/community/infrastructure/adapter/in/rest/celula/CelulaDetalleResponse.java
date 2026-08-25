package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.CelulaDetalle;
import com.renaser.os.community.domain.model.celula.Celula;

import java.util.List;

public record CelulaDetalleResponse(String id, String name, String cohortId, String videoCallUrl,
                                     String nextSessionAt, PerfilBasicoResponse mentor,
                                     List<PerfilBasicoResponse> members) {

    public static CelulaDetalleResponse from(CelulaDetalle detalle) {
        Celula c = detalle.celula();
        return new CelulaDetalleResponse(c.id().toString(), c.nombre(), c.cohorteId().toString(),
                c.urlVideollamada(), c.proximaSesionEn() != null ? c.proximaSesionEn().toString() : null,
                PerfilBasicoResponse.from(detalle.mentor()),
                detalle.miembros().stream().map(PerfilBasicoResponse::from).toList());
    }
}
