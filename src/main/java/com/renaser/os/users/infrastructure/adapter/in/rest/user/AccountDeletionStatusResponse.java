package com.renaser.os.users.infrastructure.adapter.in.rest.user;

import com.renaser.os.users.domain.model.user.EstadoBajaCuenta;

public record AccountDeletionStatusResponse(boolean bajaPendiente, String solicitadaEn, String purgaEl,
                                             Long diasRestantes, int diasDeGracia) {

    public static AccountDeletionStatusResponse from(EstadoBajaCuenta estado) {
        return new AccountDeletionStatusResponse(estado.bajaPendiente(),
                estado.solicitadaEn() == null ? null : estado.solicitadaEn().toString(),
                estado.purgaEl() == null ? null : estado.purgaEl().toString(),
                estado.diasRestantes(), estado.diasDeGracia());
    }
}
