package com.renaser.os.academy.infrastructure.adapter.in.rest.recomendacion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.renaser.os.academy.application.ports.in.recomendacion.ConsultarRecomendacionDiariaUseCase.Disponible;
import com.renaser.os.academy.application.ports.in.recomendacion.ConsultarRecomendacionDiariaUseCase.NoDisponible;
import com.renaser.os.academy.application.ports.in.recomendacion.ConsultarRecomendacionDiariaUseCase.RecomendacionDiaria;

/**
 * GET /api/v1/academia/recomendacion — espejo de `RecommendationResponse`
 * (RenaserBack `academia-adaptativa/service.ts:10-19`). `reason` se usa en
 * AMBAS ramas (motivo de la IA cuando `available:true`, codigo de motivo
 * cuando `available:false`) — asi era el wire viejo, se conserva el nombre.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecomendacionResponse(boolean available, String leccionId, String leccionTitulo, String cursoId,
                                     String cursoTitulo, String reason) {

    public static RecomendacionResponse from(RecomendacionDiaria recomendacion) {
        return switch (recomendacion) {
            case Disponible d -> new RecomendacionResponse(true, d.leccionId().value(), d.leccionTitulo(),
                    d.cursoId().value(), d.cursoTitulo(), d.motivo());
            case NoDisponible nd -> new RecomendacionResponse(false, null, null, null, null, nd.razon());
        };
    }
}
