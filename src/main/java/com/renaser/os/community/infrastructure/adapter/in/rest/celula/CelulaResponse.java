package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.CelulaResumen;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.PeriodoGrupo;

/**
 * {@code periodStart}/{@code periodEnd} (V48) van como texto ISO, mismo criterio que
 * {@code CohorteResponse} con sus fechas. Null los dos = grupo sin periodo, no caduca.
 *
 * <p>No se manda "vencido": eso depende del dia de QUIEN mira, y este adaptador no puede
 * decidirlo — la fecha del servidor es UTC y el padron vive en America/Lima (E-91, regla 02).
 * Con las dos fechas, el cliente lo resuelve en la zona correcta.
 */
public record CelulaResponse(String id, String name, String cohortId, String videoCallUrl, String nextSessionAt,
                              int memberCount, PerfilBasicoResponse mentor, String periodStart, String periodEnd) {

    public static CelulaResponse from(CelulaResumen resumen) {
        Celula c = resumen.celula();
        PeriodoGrupo periodo = c.periodo();
        return new CelulaResponse(c.id().toString(), c.nombre(), c.cohorteId().toString(), c.urlVideollamada(),
                c.proximaSesionEn() != null ? c.proximaSesionEn().toString() : null, resumen.cantidadMiembros(),
                PerfilBasicoResponse.from(resumen.mentor()),
                periodo != null ? periodo.inicio().toString() : null,
                periodo != null ? periodo.fin().toString() : null);
    }
}
