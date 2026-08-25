package com.renaser.os.notifications.infrastructure.adapter.in.rest.notificacion;

import com.renaser.os.notifications.application.ports.in.notificacion.MarcarLeidaUseCase.ResultadoLectura;

import java.time.Instant;

public record MarcarLeidaResponse(Long id, Instant readAt) {

    public static MarcarLeidaResponse from(ResultadoLectura resultado) {
        return new MarcarLeidaResponse(resultado.id(), resultado.leidaEn());
    }
}
