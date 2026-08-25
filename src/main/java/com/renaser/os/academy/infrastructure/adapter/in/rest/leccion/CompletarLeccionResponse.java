package com.renaser.os.academy.infrastructure.adapter.in.rest.leccion;

import com.renaser.os.academy.domain.model.progreso.ProgresoLeccion;

import java.time.Instant;

/** Endpoint NUEVO (reemplaza la escritura directa a `leccion_progreso`) — sin wire viejo que preservar. */
public record CompletarLeccionResponse(String leccionId, Instant completadaEn) {

    public static CompletarLeccionResponse from(ProgresoLeccion progreso) {
        return new CompletarLeccionResponse(progreso.leccionId().value(), progreso.completadaEn());
    }
}
