package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.renaser.os.users.application.ports.in.autenticacion.ConfirmarCodigoVerificacionEmailUseCase.ResultadoVerificacion;

public record VerificacionEmailResponse(String verificationToken) {

    public static VerificacionEmailResponse from(ResultadoVerificacion resultado) {
        return new VerificacionEmailResponse(resultado.verificationToken());
    }
}
