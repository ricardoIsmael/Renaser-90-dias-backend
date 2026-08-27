package com.renaser.os.academy.infrastructure.adapter.in.rest.clasediaria;

import com.renaser.os.academy.application.ports.in.clasediaria.CompletarClaseDiariaUseCase.ClaseDiariaCompletada;

/**
 * Espejo del shape de respuesta de {@code completeClaseDiaria}
 * (RenaserBack `clase-diaria/service.ts:64,85`: {@code { leccionId, habitTrackId }}).
 */
public record CompletarClaseDiariaResponse(String leccionId, String habitTrackId, int puntosOtorgados) {

    public static CompletarClaseDiariaResponse from(ClaseDiariaCompletada completada) {
        return new CompletarClaseDiariaResponse(completada.leccionId().value(),
                completada.registroHabitoId().toString(), completada.puntosOtorgados());
    }
}
