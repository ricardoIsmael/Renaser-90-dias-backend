package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase.OcurrenciaVista;

/** Espejo de {@code EventOccurrence} (types.ts, app instalada). */
record OcurrenciaResponse(EventoResponse event, String occurrenceStart, String startsAt, Integer durationMinutes,
                           String title, String viewerRsvpStatus) {

    static OcurrenciaResponse from(OcurrenciaVista v) {
        return new OcurrenciaResponse(
                EventoResponse.from(v.evento(), v.coverUrl()),
                v.inicioOcurrencia().toString(),
                v.iniciaEn().toString(),
                v.duracionMinutos(),
                v.titulo(),
                v.viewerRsvpStatus() == null ? null : EventoWireMapper.toWireEstadoConfirmacion(v.viewerRsvpStatus()));
    }
}
