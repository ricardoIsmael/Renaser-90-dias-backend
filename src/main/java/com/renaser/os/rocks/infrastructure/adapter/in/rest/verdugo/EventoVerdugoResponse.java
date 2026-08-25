package com.renaser.os.rocks.infrastructure.adapter.in.rest.verdugo;

import com.renaser.os.rocks.domain.model.verdugo.EventoVerdugo;

import java.time.Instant;
import java.util.UUID;

public record EventoVerdugoResponse(UUID id, String destinoTipo, UUID destinoId, Instant disparadoEn,
                                     String resultado, Instant resueltoEn) {

    public static EventoVerdugoResponse from(EventoVerdugo e) {
        String resultado = e.resultado() == null ? null : e.resultado().name();
        return new EventoVerdugoResponse(e.id().value(), e.destinoTipo().name(), e.destinoId(), e.disparadoEn(),
                resultado, e.resueltoEn());
    }
}
