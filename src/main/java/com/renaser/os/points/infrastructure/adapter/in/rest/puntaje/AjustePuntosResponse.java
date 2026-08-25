package com.renaser.os.points.infrastructure.adapter.in.rest.puntaje;

import com.renaser.os.points.domain.model.ajuste.AjustePuntos;

public record AjustePuntosResponse(Long id, int deltaAplicado, int saldoPosterior) {

    public static AjustePuntosResponse from(AjustePuntos ajuste) {
        return new AjustePuntosResponse(ajuste.id(), ajuste.deltaAplicado(), ajuste.saldoPosterior());
    }
}
