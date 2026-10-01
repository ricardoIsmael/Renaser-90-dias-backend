package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarObservacionesUseCase.PaginaDeObservaciones;

import java.time.Instant;
import java.util.List;

/** @param nextBefore el cursor de la página siguiente ({@code ?before=}); null si no hay más */
public record ObservationPageResponse(List<ObservationResponse> items, Instant nextBefore) {

    static ObservationPageResponse from(PaginaDeObservaciones pagina) {
        return new ObservationPageResponse(pagina.observaciones().stream().map(ObservationResponse::from).toList(),
                pagina.siguiente());
    }
}
