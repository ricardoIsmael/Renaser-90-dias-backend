package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.renaser.os.plataforma.application.ports.in.VisibilidadDelCodigoRenaser;
import com.renaser.os.users.api.UserRole;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Cómo está «Código Renaser en las fichas» (contrato de la spec 034, §1). */
record CodigoRenaserEnFichasResponse(boolean visible, boolean mixto, Map<UserRole, Boolean> porRol,
                                     UltimoCambioResponse ultimoCambio) {

    record UltimoCambioResponse(UUID porId, String porNombre, Instant en) {
    }

    static CodigoRenaserEnFichasResponse from(VisibilidadDelCodigoRenaser vista) {
        var ultimo = vista.ultimoCambio();
        return new CodigoRenaserEnFichasResponse(vista.visible(), vista.mixto(), vista.porRol(),
                ultimo == null ? null : new UltimoCambioResponse(ultimo.porId().value(), ultimo.porNombre(), ultimo.en()));
    }
}
