package com.renaser.os.users.application.ports.in.autenticacion;

import com.renaser.os.shared.application.SelfValidating;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public interface SolicitarResetContrasenaUseCase {

    /**
     * Responde SIEMPRE de la misma forma exista o no una cuenta con ese email — misma
     * no-enumeracion que ya aplica {@code IniciarSesionUseCase} (CLAUDE.MD §5.3.3): si no hay
     * cuenta con ese email, o la cuenta existe pero solo entra por proveedor social (sin
     * contrasena), este metodo no hace mas que registrar el intento contra el limite de tasa y
     * termina en silencio, sin lanzar una excepcion distinta ni dejar una diferencia observable.
     *
     * @throws com.renaser.os.shared.domain.RateLimitExceededException si se supero el limite de
     * solicitudes por email o por IP en la ventana actual
     */
    void solicitar(SolicitarResetContrasenaCommand command);

    /** {@code requestIp} puede ser null (mismo criterio que {@code SubmitAccountRequestCommand}): sin IP, se omite ese lado del limite de tasa. */
    record SolicitarResetContrasenaCommand(@NotBlank @Email String email, String requestIp) {
        public SolicitarResetContrasenaCommand {
            SelfValidating.validateConstructorArgs(SolicitarResetContrasenaCommand.class, email, requestIp);
        }
    }
}
