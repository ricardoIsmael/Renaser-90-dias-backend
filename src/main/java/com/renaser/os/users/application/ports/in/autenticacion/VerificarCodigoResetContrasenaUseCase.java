package com.renaser.os.users.application.ports.in.autenticacion;

import com.renaser.os.shared.application.SelfValidating;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Segundo paso del reset por codigo (2026-09-04, D-102): comprueba el codigo de 6 digitos y,
 * si coincide, emite el MISMO token de reset de un solo uso que ya usa el flujo por link
 * ({@code TokenResetContrasenaPort}). El tercer paso es el que ya existia:
 * {@link ConfirmarResetContrasenaUseCase} con ese token y la contrasena nueva — no hay un
 * "confirmar por codigo" aparte, el codigo solo sirve para obtener el token.
 *
 * <p>Espejo exacto de {@link ConfirmarCodigoVerificacionEmailUseCase} en el alta: alli el
 * codigo se canjea por un {@code verificationToken}; aca por un {@code resetToken}.
 */
public interface VerificarCodigoResetContrasenaUseCase {

    /**
     * @throws com.renaser.os.shared.domain.CodigoVerificacionInvalidoException si el codigo no
     * coincide, ya vencio, se agotaron los intentos, o el correo no tiene una cuenta con
     * contrasena — los cuatro colapsan en el mismo error, para no revelar cual fue
     */
    ResultadoVerificacionReset verificarCodigo(VerificarCodigoResetContrasenaCommand command);

    /** Siempre 6 digitos: la validacion de forma falla con 400 antes de gastar un intento real. */
    record VerificarCodigoResetContrasenaCommand(@NotBlank @Email String email,
                                                  @NotBlank @Pattern(regexp = "\\d{6}") String codigo) {
        public VerificarCodigoResetContrasenaCommand {
            SelfValidating.validateConstructorArgs(VerificarCodigoResetContrasenaCommand.class, email, codigo);
        }

        /** El codigo es una credencial de un solo uso: no se loguea. */
        @Override
        public String toString() {
            return "VerificarCodigoResetContrasenaCommand[email=" + email + ", codigo=oculto]";
        }
    }

    record ResultadoVerificacionReset(String resetToken) {

        @Override
        public String toString() {
            return "ResultadoVerificacionReset[resetToken=oculto]";
        }
    }
}
