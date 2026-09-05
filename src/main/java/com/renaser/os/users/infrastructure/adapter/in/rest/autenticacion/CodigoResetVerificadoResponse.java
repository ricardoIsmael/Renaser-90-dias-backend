package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.renaser.os.users.application.ports.in.autenticacion.VerificarCodigoResetContrasenaUseCase.ResultadoVerificacionReset;

/**
 * El {@code resetToken} es el mismo token de un solo uso que acepta
 * {@code POST /api/v1/auth/password/reset-confirm}: la app lo guarda en memoria y lo manda
 * junto con la contrasena nueva. Espejo de {@link VerificacionEmailResponse} en el alta.
 */
public record CodigoResetVerificadoResponse(String resetToken) {

    public static CodigoResetVerificadoResponse from(ResultadoVerificacionReset resultado) {
        return new CodigoResetVerificadoResponse(resultado.resetToken());
    }
}
