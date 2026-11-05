package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase.OcurrenciaVista;

/** Espejo de {@code EventOccurrence} (types.ts, app instalada). */
record OcurrenciaResponse(EventoResponse event, String occurrenceStart, String startsAt, Integer durationMinutes,
                           String title, String viewerRsvpStatus) {

    static OcurrenciaResponse from(OcurrenciaVista v) {
        EventoResponse evento = EventoResponse.from(v.evento(), v.coverUrl());
        // Un evento con Meet automatico que se repite: cada fecha abre SU enlace, nunca el de otra (spec 040, FR-017).
        if (v.ubicacionDeMeet() != null) {
            evento = evento.conUbicacion(v.ubicacionDeMeet().enlace());
        }
        return new OcurrenciaResponse(
                evento,
                v.inicioOcurrencia().toString(),
                v.iniciaEn().toString(),
                v.duracionMinutos(),
                v.titulo(),
                v.viewerRsvpStatus() == null ? null : EventoWireMapper.toWireEstadoConfirmacion(v.viewerRsvpStatus()));
    }
}
