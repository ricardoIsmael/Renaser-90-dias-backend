package com.renaser.os.users.infrastructure.adapter.in.rest.eliminacion;

import com.renaser.os.users.domain.model.user.EstadoBajaCuenta;

import java.time.Instant;

/** Cuando se cerro y cuando se borra para siempre (instantes; la app los muestra en la zona del telefono). */
public record CuentaCerradaResponse(Instant cerradaEn, Instant seBorraEl, int diasDeGracia) {

    public static CuentaCerradaResponse from(EstadoBajaCuenta estado) {
        return new CuentaCerradaResponse(estado.solicitadaEn(), estado.purgaEl(), estado.diasDeGracia());
    }
}
