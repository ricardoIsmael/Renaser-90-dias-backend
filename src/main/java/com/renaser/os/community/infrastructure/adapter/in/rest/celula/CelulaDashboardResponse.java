package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarDashboardCelulasUseCase.CelulaConCohorte;
import com.renaser.os.community.domain.model.cohorte.EstadoCohorte;

/**
 * #25 (docs/PLAN_INTEGRACION_FRONTEND.md sec. 5): panel admin cross-cohorte —
 * {@code GET /api/v1/admin/cells/dashboard}. Sin {@code rankingPosition}/
 * {@code coherenceScoreGroup}: siguen viviendo en `points`, que hoy no expone ningun
 * puerto publico de ranking por celula (`points.api` solo tiene los finders de
 * porcentaje EN LOTE de habitos/rocas/cursos) — mismo gap ya documentado en CM-12,
 * docs/MODULO_COMMUNITY.md sec. 5. Un futuro agregador puede sumar esos dos campos sin
 * tocar este endpoint.
 */
public record CelulaDashboardResponse(String id, String name, String cohortId, String cohortName,
                                       String cohortStatus, String videoCallUrl, String nextSessionAt,
                                       int memberCount, PerfilBasicoResponse mentor) {

    public static CelulaDashboardResponse from(CelulaConCohorte item) {
        var c = item.celula();
        var cohorte = item.cohorte();
        return new CelulaDashboardResponse(c.id().toString(), c.nombre(), c.cohorteId().toString(), cohorte.nombre(),
                toWireEstado(cohorte.estado()), c.urlVideollamada(),
                c.proximaSesionEn() != null ? c.proximaSesionEn().toString() : null, item.cantidadMiembros(),
                PerfilBasicoResponse.from(item.mentor()));
    }

    private static String toWireEstado(EstadoCohorte estado) {
        return switch (estado) {
            case PLANIFICADA -> "PLANNED";
            case ACTIVA -> "ACTIVE";
            case COMPLETADA -> "COMPLETED";
        };
    }
}
