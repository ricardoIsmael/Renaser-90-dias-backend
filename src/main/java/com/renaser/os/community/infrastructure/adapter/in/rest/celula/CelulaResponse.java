package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.CelulaResumen;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.PeriodoGrupo;

/**
 * {@code periodStart}/{@code periodEnd} (V48) van como texto ISO, mismo criterio que
 * {@code CohorteResponse} con sus fechas. Null los dos = grupo sin periodo, no caduca.
 *
 * <p>{@code status} SI viaja, y lo calcula el servidor. La version anterior de este comentario
 * decia que no se podia mandar porque "depende del dia de quien mira": eso vale para el dia
 * personal del alumno, no para el del grupo. Un grupo es del programa, y su dia es el de la zona
 * del programa que fija el backend — si lo decidiera cada telefono, dos aprendices en husos
 * distintos verian cerrar el mismo grupo en dias distintos (plan.md §3). El adaptador no lo
 * resuelve: llega ya resuelto desde el caso de uso.
 *
 * <p>{@code learnerCount} es la ocupacion real del historial, la que mide el cupo;
 * {@code memberCount} sigue siendo la del puntero y responde otra pregunta.
 */
public record CelulaResponse(String id, String name, String cohortId, String videoCallUrl, String nextSessionAt,
                              int memberCount, PerfilBasicoResponse mentor, String periodStart, String periodEnd,
                              String status, String type, int learnerCount, Integer capacity) {

    public static CelulaResponse from(CelulaResumen resumen) {
        Celula c = resumen.celula();
        PeriodoGrupo periodo = c.periodo();
        return new CelulaResponse(c.id().toString(), c.nombre(), c.cohorteId().toString(), c.urlVideollamada(),
                c.proximaSesionEn() != null ? c.proximaSesionEn().toString() : null, resumen.cantidadMiembros(),
                PerfilBasicoResponse.from(resumen.mentor()),
                periodo != null ? periodo.inicio().toString() : null,
                periodo != null ? periodo.fin().toString() : null,
                resumen.estado().name(), c.tipo().name(), resumen.aprendicesVigentes(), resumen.cupoMaximo());
    }
}
