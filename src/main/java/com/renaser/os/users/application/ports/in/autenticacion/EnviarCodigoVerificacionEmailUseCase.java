package com.renaser.os.users.application.ports.in.autenticacion;

import com.renaser.os.shared.application.SelfValidating;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Primer paso de verificar que el solicitante controla su correo, ANTES del alta (2026-08-27,
 * reemplaza el {@code signInWithOtp} de Supabase que el registro ya no puede usar).
 *
 * <p>A diferencia de {@link SolicitarResetContrasenaUseCase}, este SI responde siempre 202 sin
 * ninguna comprobacion de "existe una cuenta con ese email": el punto entero de este flujo es
 * para gente que TODAVIA no tiene cuenta — no hay nada que enumerar.
 */
public interface EnviarCodigoVerificacionEmailUseCase {

    /**
     * @throws com.renaser.os.shared.domain.RateLimitExceededException si se supero el limite de
     * solicitudes por email o por IP en la ventana actual
     */
    void enviar(EnviarCodigoVerificacionEmailCommand command);

    /** {@code requestIp} puede ser null, mismo criterio que {@code SubmitAccountRequestCommand}. */
    record EnviarCodigoVerificacionEmailCommand(@NotBlank @Email String email, String requestIp) {
        public EnviarCodigoVerificacionEmailCommand {
            SelfValidating.validateConstructorArgs(EnviarCodigoVerificacionEmailCommand.class, email, requestIp);
        }
    }
}
