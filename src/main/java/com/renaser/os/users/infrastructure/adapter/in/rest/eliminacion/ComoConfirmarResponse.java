package com.renaser.os.users.infrastructure.adapter.in.rest.eliminacion;

import com.renaser.os.users.application.ports.in.eliminacion.CerrarMiCuentaUseCase.ComoConfirmar;

/** {@code confirmaCon}: {@code CONTRASENA} o {@code CODIGO}. */
public record ComoConfirmarResponse(String confirmaCon, int diasDeGracia) {

    public static ComoConfirmarResponse from(ComoConfirmar como) {
        return new ComoConfirmarResponse(como.metodo().name(), como.diasDeGracia());
    }
}
