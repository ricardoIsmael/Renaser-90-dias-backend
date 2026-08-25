package com.renaser.os.onboarding.infrastructure.adapter.in.rest.respuesta;

import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;

import java.math.BigDecimal;
import java.time.Instant;

public record RespuestaResponse(Long id, int questionId, String textValue, BigDecimal numberValue,
                                 Boolean booleanValue, Short scaleValue, String jsonValue, Long mediaId,
                                 Instant acceptedAt, Instant answeredAt, Instant updatedAt) {

    public static RespuestaResponse from(Respuesta r) {
        return new RespuestaResponse(r.id(), r.preguntaId(), r.valorTexto(), r.valorNumero(), r.valorBooleano(),
                r.valorEscala(), r.valorJson(), r.mediaId(), r.aceptadaEn(), r.respondidaEn(), r.actualizadoEn());
    }
}
