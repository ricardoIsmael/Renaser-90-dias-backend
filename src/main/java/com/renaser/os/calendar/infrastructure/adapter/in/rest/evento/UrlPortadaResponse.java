package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import com.renaser.os.calendar.application.ports.in.evento.SolicitarUrlPortadaUseCase.UrlPortada;

record UrlPortadaResponse(String url, String bucket, String ruta) {

    static UrlPortadaResponse from(UrlPortada u) {
        return new UrlPortadaResponse(u.url().toString(), u.bucket(), u.ruta());
    }
}
