package com.renaser.os.users.application.ports.in.autenticacion;

import com.renaser.os.shared.application.SelfValidating;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Primer paso de recuperar la contrasena POR CODIGO, dentro de la app (2026-09-04, D-102):
 * manda un codigo de 6 digitos al correo. Convive con {@link SolicitarResetContrasenaUseCase}
 * (que manda un LINK, pensado para un frontend web que todavia no existe) sin reemplazarlo.
 *
 * <p>Misma no-enumeracion que el reset por link (CLAUDE.MD §5.3.3): exista o no la cuenta, o
 * aunque exista pero solo entre por proveedor social, el resultado observable es el mismo —
 * termina en silencio. Solo se manda correo cuando hay una cuenta con contrasena.
 */
public interface SolicitarCodigoResetContrasenaUseCase {

    /**
     * @throws com.renaser.os.shared.domain.RateLimitExceededException si se supero el limite de
     * solicitudes por email o por IP en la ventana actual — el mismo contador que el reset por
     * link, porque para el limite son la misma cosa: "alguien pidio recuperar esta cuenta"
     */
    void solicitarCodigo(SolicitarCodigoResetContrasenaCommand command);

    /** {@code requestIp} puede ser null, mismo criterio que {@code SolicitarResetContrasenaCommand}. */
    record SolicitarCodigoResetContrasenaCommand(@NotBlank @Email String email, String requestIp) {
        public SolicitarCodigoResetContrasenaCommand {
            SelfValidating.validateConstructorArgs(SolicitarCodigoResetContrasenaCommand.class, email, requestIp);
        }
    }
}
