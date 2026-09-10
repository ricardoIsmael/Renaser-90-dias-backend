package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.CelulaDetalle;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.PeriodoGrupo;

import java.util.List;

/** {@code periodStart}/{@code periodEnd}: ver la nota de {@link CelulaResponse}. */
public record CelulaDetalleResponse(String id, String name, String cohortId, String videoCallUrl,
                                     String nextSessionAt, PerfilBasicoResponse mentor,
                                     List<PerfilBasicoResponse> members, String periodStart, String periodEnd) {

    public static CelulaDetalleResponse from(CelulaDetalle detalle) {
        Celula c = detalle.celula();
        PeriodoGrupo periodo = c.periodo();
        return new CelulaDetalleResponse(c.id().toString(), c.nombre(), c.cohorteId().toString(),
                c.urlVideollamada(), c.proximaSesionEn() != null ? c.proximaSesionEn().toString() : null,
                PerfilBasicoResponse.from(detalle.mentor()),
                detalle.miembros().stream().map(PerfilBasicoResponse::from).toList(),
                periodo != null ? periodo.inicio().toString() : null,
                periodo != null ? periodo.fin().toString() : null);
    }
}
